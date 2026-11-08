import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ESTADOS, Estado, Pedido, PedidosApi } from './pedidos-api';
import { TablaPedidos } from './tabla-pedidos';

const SEGUNDOS_PARA_DESHACER = 5;
const DURACION_AVISO_MS = 4000;

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, TablaPedidos],
  template: `
    <header class="cabecera">
      <div class="marco cabecera-interior">
        <div>
          <h1>Gestión de pedidos</h1>
          <p class="subtitulo">Sistema distribuido tolerante a fallos</p>
        </div>
        <p class="nodo" [class.nodo-caido]="!nodo()">
          <span class="punto" aria-hidden="true"></span>
          @if (nodo(); as actual) {
            Atendido por <strong>{{ actual }}</strong>
          } @else {
            Sin respuesta de los nodos
          }
        </p>
      </div>
    </header>

    <main class="marco contenido">
      <section class="panel formulario-panel" aria-labelledby="titulo-formulario">
        <form class="formulario" ngNativeValidate [formGroup]="formulario" (ngSubmit)="guardar()">
          <h2 id="titulo-formulario">{{ editando() ? 'Editar pedido ' + editando()!.id : 'Nuevo pedido' }}</h2>
          <label>
            Cliente
            <input #campoCliente formControlName="cliente" required maxlength="100" autocomplete="off" />
          </label>
          <label>
            Producto
            <input formControlName="producto" required maxlength="100" autocomplete="off" />
          </label>
          <label>
            Cantidad
            <input formControlName="cantidad" type="number" required min="1" max="10000" step="1" />
          </label>
          <div class="acciones">
            <button type="submit" [disabled]="enviando()">{{ editando() ? 'Guardar cambios' : 'Crear pedido' }}</button>
            @if (editando()) {
              <button type="button" class="secundario" (click)="cancelar()">Cancelar</button>
            }
          </div>
        </form>
      </section>

      <div class="columna-lista">
        <ul class="resumen" aria-label="Pedidos por estado">
          @for (grupo of resumen(); track grupo.estado) {
            <li [attr.data-estado]="grupo.estado">
              <span class="resumen-total">{{ grupo.total }}</span>
              <span class="resumen-estado">{{ grupo.estado }}</span>
            </li>
          }
        </ul>

        <section class="panel" aria-labelledby="titulo-lista">
          <h2 #tituloLista id="titulo-lista" tabindex="-1">Pedidos ({{ visibles().length }})</h2>
          <app-tabla-pedidos
            [pedidos]="visibles()"
            [cargando]="cargando()"
            (cambiarEstado)="cambiarEstado($event.pedido, $event.estado)"
            (editar)="editar($event)"
            (eliminar)="eliminar($event)"
          />
        </section>
      </div>
    </main>

    <div class="avisos" role="status">
      @for (pendiente of porEliminarLista(); track pendiente.pedido.id) {
        <div class="aviso aviso-deshacer">
          <p>Se eliminará el pedido {{ pendiente.pedido.id }} de {{ pendiente.pedido.cliente }}.</p>
          <button
            #botonDeshacer
            type="button"
            class="secundario"
            (click)="deshacer()"
            [attr.aria-label]="'Deshacer la eliminación del pedido ' + pendiente.pedido.id"
          >
            Deshacer <span aria-hidden="true">({{ pendiente.segundos }} s)</span>
          </button>
          <span class="cuenta" aria-hidden="true"></span>
        </div>
      }
      @if (aviso(); as actual) {
        <div class="aviso" [class.aviso-error]="actual.tipo === 'error'">
          <p>{{ actual.texto }}</p>
          <button type="button" class="cerrar" (click)="aviso.set(null)" aria-label="Cerrar aviso">×</button>
        </div>
      }
    </div>
  `,
})
export class App {
  private readonly api = inject(PedidosApi);
  private readonly injector = inject(Injector);
  private readonly botonDeshacer = viewChild<ElementRef<HTMLButtonElement>>('botonDeshacer');
  private readonly campoCliente = viewChild.required<ElementRef<HTMLInputElement>>('campoCliente');
  private readonly tituloLista = viewChild.required<ElementRef<HTMLHeadingElement>>('tituloLista');

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly cargando = signal(true);
  protected readonly enviando = signal(false);
  protected readonly nodo = signal<string | null>(null);
  protected readonly aviso = signal<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  private readonly porEliminar = signal<{ pedido: Pedido; segundos: number } | null>(null);
  private readonly editandoId = signal<number | null>(null);
  private cuentaRegresiva?: ReturnType<typeof setInterval>;
  private ocultarAviso?: ReturnType<typeof setTimeout>;

  protected readonly editando = computed(() => this.pedidos().find((p) => p.id === this.editandoId()));
  // El pedido que espera su eliminación se oculta de la lista mientras se puede deshacer.
  protected readonly visibles = computed(() => this.pedidos().filter((p) => p.id !== this.porEliminar()?.pedido.id));
  // Como lista, para que cada pedido nuevo reinicie la barra de cuenta regresiva.
  protected readonly porEliminarLista = computed(() => [this.porEliminar()].filter((p) => p !== null));
  protected readonly resumen = computed(() =>
    ESTADOS.map((estado) => ({ estado, total: this.visibles().filter((p) => p.estado === estado).length })),
  );

  protected readonly formulario = new FormGroup({
    cliente: new FormControl('', { nonNullable: true }),
    producto: new FormControl('', { nonNullable: true }),
    cantidad: new FormControl(1, { nonNullable: true }),
  });

  constructor() {
    void this.cargar();
  }

  protected async guardar() {
    const pedido = this.editando();
    const datos = this.formulario.getRawValue();
    this.enviando.set(true);
    const ok = pedido
      ? await this.ejecutar(() => this.api.pedir('PUT', `/pedidos/${pedido.id}`, datos), `Pedido ${pedido.id} actualizado.`)
      : await this.ejecutar(() => this.api.pedir('POST', '/pedidos', datos), 'Pedido creado.');
    this.enviando.set(false);
    if (ok) this.cancelar();
  }

  // Solo se envía el estado: así no se sobrescriben datos editados desde otra ventana.
  protected cambiarEstado({ id }: Pedido, estado: Estado) {
    void this.ejecutar(() => this.api.pedir('PUT', `/pedidos/${id}/estado`, { estado }), `Pedido ${id}: ${estado}.`);
  }

  protected editar({ id, cliente, producto, cantidad }: Pedido) {
    this.editandoId.set(id);
    this.formulario.setValue({ cliente, producto, cantidad });
    this.campoCliente().nativeElement.focus();
  }

  protected cancelar() {
    this.editandoId.set(null);
    this.formulario.reset();
  }

  protected eliminar(pedido: Pedido) {
    void this.confirmarEliminacion();
    if (pedido.id === this.editandoId()) this.cancelar();
    this.porEliminar.set({ pedido, segundos: SEGUNDOS_PARA_DESHACER });
    this.cuentaRegresiva = setInterval(() => {
      const actual = this.porEliminar();
      if (actual && actual.segundos > 1) this.porEliminar.set({ ...actual, segundos: actual.segundos - 1 });
      else void this.confirmarEliminacion();
    }, 1000);
    // La fila oculta tenía el foco; se lleva a Deshacer para poder usarlo también con el teclado.
    afterNextRender(() => this.botonDeshacer()?.nativeElement.focus(), { injector: this.injector });
  }

  protected deshacer() {
    clearInterval(this.cuentaRegresiva);
    this.quitarAvisoDeshacer();
  }

  private async confirmarEliminacion() {
    clearInterval(this.cuentaRegresiva);
    const pedido = this.porEliminar()?.pedido;
    if (!pedido) return;
    this.pedidos.update((lista) => lista.filter((p) => p.id !== pedido.id));
    this.quitarAvisoDeshacer();
    const ok = await this.ejecutar(() => this.api.pedir('DELETE', `/pedidos/${pedido.id}`), `Pedido ${pedido.id} eliminado.`);
    if (!ok) this.pedidos.update((lista) => [...lista, pedido].sort((a, b) => b.id - a.id));
  }

  // Si el foco estaba en el aviso que desaparece, pasa al título de la lista para no perderlo.
  private quitarAvisoDeshacer() {
    const teniaFoco = document.activeElement === this.botonDeshacer()?.nativeElement;
    this.porEliminar.set(null);
    if (teniaFoco) this.tituloLista().nativeElement.focus();
  }

  private async ejecutar(accion: () => Promise<unknown>, exito: string): Promise<boolean> {
    try {
      await accion();
      this.avisar('exito', exito);
      await this.cargar();
      return true;
    } catch (error) {
      this.avisar('error', (error as Error).message);
      return false;
    }
  }

  // Los avisos de éxito se ocultan solos; los de error quedan hasta que el usuario los cierra.
  private avisar(tipo: 'exito' | 'error', texto: string) {
    clearTimeout(this.ocultarAviso);
    this.aviso.set({ tipo, texto });
    if (tipo === 'exito') this.ocultarAviso = setTimeout(() => this.aviso.set(null), DURACION_AVISO_MS);
  }

  private async cargar() {
    try {
      const { datos, nodo } = await this.api.pedir<Pedido[]>('GET', '/pedidos');
      this.pedidos.set(datos);
      this.nodo.set(nodo);
    } catch (error) {
      this.nodo.set(null);
      this.avisar('error', (error as Error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
