import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export const ESTADOS = ['pendiente', 'en proceso', 'enviado', 'entregado'] as const;
export type Estado = (typeof ESTADOS)[number];

export interface Pedido {
  id: number;
  cliente: string;
  producto: string;
  cantidad: number;
  estado: Estado;
}

/** Datos que se envían al crear o actualizar; sin estado, la API conserva el actual. */
export type DatosPedido = Omit<Pedido, 'id' | 'estado'> & { estado?: Estado };

const SIN_CONEXION = 'No se pudo conectar con el servidor. Verifique que el sistema esté en ejecución.';
const DATOS_INVALIDOS = 'Revise los datos: textos de 1 a 100 caracteres y cantidad entre 1 y 10000.';
const SIN_NODOS = 'No hay nodos disponibles para atender la solicitud. Intente de nuevo en unos segundos.';

/** Llama a la API REST y devuelve los datos junto con el nodo que atendió la petición. */
@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);

  async pedir<T>(
    metodo: string,
    ruta: string,
    cuerpo?: DatosPedido | Pick<Pedido, 'estado'>,
  ): Promise<{ datos: T; nodo: string | null }> {
    try {
      const respuesta = await firstValueFrom(
        this.http.request<T>(metodo, `/api${ruta}`, { body: cuerpo, observe: 'response' }),
      );
      return { datos: respuesta.body as T, nodo: respuesta.headers.get('X-Node') };
    } catch (error) {
      throw new Error(mensajeDeError(error as HttpErrorResponse), { cause: error });
    }
  }
}

function mensajeDeError(error: HttpErrorResponse): string {
  if (error.status === 0) return SIN_CONEXION;
  if (error.status === 400) return DATOS_INVALIDOS;
  return typeof error.error?.detail === 'string' ? error.error.detail : SIN_NODOS;
}
