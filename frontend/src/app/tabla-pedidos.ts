import { Component, input, output } from '@angular/core';
import { ESTADOS, Estado, Pedido } from './pedidos-api';

@Component({
  selector: 'app-tabla-pedidos',
  template: `
    @if (cargando()) {
      <p class="vacio">Cargando pedidos…</p>
    } @else if (pedidos().length === 0) {
      <p class="vacio">Aún no hay pedidos. Registre el primero con el formulario.</p>
    } @else {
      <div class="tabla-contenedor" role="region" aria-labelledby="titulo-lista" tabindex="0">
        <table>
          <thead>
            <tr>
              <th scope="col" class="numero">N.º</th>
              <th scope="col">Cliente</th>
              <th scope="col">Producto</th>
              <th scope="col" class="numero">Cantidad</th>
              <th scope="col">Estado</th>
              <th scope="col"><span class="oculto">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            @for (pedido of pedidos(); track pedido.id) {
              <tr>
                <td class="numero">{{ pedido.id }}</td>
                <td class="texto">{{ pedido.cliente }}</td>
                <td class="texto">{{ pedido.producto }}</td>
                <td class="numero">{{ pedido.cantidad }}</td>
                <td>
                  <div class="estado" [attr.data-estado]="pedido.estado">
                    <span class="pista" aria-hidden="true">
                      @for (estado of estados; track estado; let paso = $index) {
                        <span class="tramo" [class.activo]="paso <= estados.indexOf(pedido.estado)"></span>
                      }
                    </span>
                    <select
                      #selector
                      (change)="cambiar(pedido, selector)"
                      [attr.aria-label]="'Estado del pedido ' + pedido.id"
                    >
                      @for (estado of estados; track estado) {
                        <option [value]="estado" [selected]="estado === pedido.estado">{{ estado }}</option>
                      }
                    </select>
                  </div>
                </td>
                <td class="acciones-fila">
                  <button
                    type="button"
                    class="secundario"
                    (click)="editar.emit(pedido)"
                    [attr.aria-label]="'Editar pedido ' + pedido.id"
                  >
                    Editar
                  </button>
                  <button
                    type="button"
                    class="peligro"
                    (click)="eliminar.emit(pedido)"
                    [attr.aria-label]="'Eliminar pedido ' + pedido.id"
                  >
                    Eliminar
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class TablaPedidos {
  readonly pedidos = input.required<Pedido[]>();
  readonly cargando = input.required<boolean>();
  readonly cambiarEstado = output<{ pedido: Pedido; estado: Estado }>();
  readonly editar = output<Pedido>();
  readonly eliminar = output<Pedido>();
  protected readonly estados = ESTADOS;

  protected cambiar(pedido: Pedido, selector: HTMLSelectElement) {
    const estado = selector.value as Estado;
    // Se sigue mostrando el estado guardado hasta que el servidor confirme el cambio.
    selector.value = pedido.estado;
    this.cambiarEstado.emit({ pedido, estado });
  }
}
