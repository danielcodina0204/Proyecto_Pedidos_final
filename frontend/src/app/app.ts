import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Estado, Pedido, PedidosApi } from './pedidos-api';
import { TablaPedidos } from './tabla-pedidos';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, TablaPedidos],
  template: `
    <header class="cabecera">
      <h1>Gestión de pedidos</h1>
      <p class="nodo">
        @if (nodo(); as actual) {
          Atendido por: <strong>{{ actual }}</strong>
        } @else {
          Sin respuesta de los nodos
        }
      </p>
    </header>

    <main class="contenido">
      <p
        role="status"
        class="aviso"
        [class.aviso-exito]="aviso()?.tipo === 'exito'"
        [class.aviso-error]="aviso()?.tipo === 'error'"
      >{{ aviso()?.texto }}</p>

      <section class="panel" aria-labelledby="titulo-formulario">
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

      <section class="panel" aria-labelledby="titulo-lista">
        <h2 #tituloLista id="titulo-lista" tabindex="-1">Pedidos ({{ pedidos().length }})</h2>
        <app-tabla-pedidos
          [pedidos]="pedidos()"
          [cargando]="cargando()"
          (cambiarEstado)="cambiarEstado($event.pedido, $event.estado)"
          (editar)="editar($event)"
          (eliminar)="eliminar($event)"
        />
      </section>
    </main>
  `,
})
export class App {
  private readonly api = inject(PedidosApi);
  private readonly campoCliente = viewChild.required<ElementRef<HTMLInputElement>>('campoCliente');
  private readonly tituloLista = viewChild.required<ElementRef<HTMLHeadingElement>>('tituloLista');

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly cargando = signal(true);
  protected readonly enviando = signal(false);
  protected readonly nodo = signal<string | null>(null);
  protected readonly aviso = signal<{ tipo: 'exito' | 'error'; texto: string } | null>(null);
  private readonly editandoId = signal<number | null>(null);
  protected readonly editando = computed(() => this.pedidos().find((p) => p.id === this.editandoId()));

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

  protected async eliminar(pedido: Pedido) {
    if (!window.confirm(`¿Eliminar el pedido ${pedido.id} de ${pedido.cliente}? Esta acción no se puede deshacer.`)) return;
    if (pedido.id === this.editandoId()) this.cancelar();
    const ok = await this.ejecutar(() => this.api.pedir('DELETE', `/pedidos/${pedido.id}`), `Pedido ${pedido.id} eliminado.`);
    // La fila eliminada tenía el foco; se lleva al título de la lista para no perderlo.
    if (ok) this.tituloLista().nativeElement.focus();
  }

  private async ejecutar(accion: () => Promise<unknown>, exito: string): Promise<boolean> {
    try {
      await accion();
      this.aviso.set({ tipo: 'exito', texto: exito });
      await this.cargar();
      return true;
    } catch (error) {
      this.aviso.set({ tipo: 'error', texto: (error as Error).message });
      return false;
    }
  }

  private async cargar() {
    try {
      const { datos, nodo } = await this.api.pedir<Pedido[]>('GET', '/pedidos');
      this.pedidos.set(datos);
      this.nodo.set(nodo);
    } catch (error) {
      this.aviso.set({ tipo: 'error', texto: (error as Error).message });
    } finally {
      this.cargando.set(false);
    }
  }
}
