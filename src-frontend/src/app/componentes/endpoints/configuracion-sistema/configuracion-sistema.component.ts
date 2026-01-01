import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';

interface Usuario {
  id: number;
  nombre: string;
  apellidos: string;
  email: string;
  rol: string;
  authProvider?: string;
}

interface RoleSelection {
  ROLE_USER: boolean;
  ROLE_MANAGER: boolean;
  ROLE_ADMIN: boolean;
}

@Component({
  selector: 'app-configuracion-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent],
  templateUrl: './configuracion-sistema.component.html',
  styleUrls: ['./configuracion-sistema.component.scss']
})
export class ConfiguracionSistemaComponent implements OnInit {
  limiteTurno: number = 36;
  limiteTurnoOriginal: number = 36;
  cargando: boolean = true;
  cargandoRoles: boolean = true;

  usuarios: Usuario[] = [];
  filtroUsuarios: string = '';
  rolesSeleccionados: Map<number, RoleSelection> = new Map();
  paginaActualUsuarios: number = 1;
  tamanoPaginaUsuarios: number = 5;
  totalPaginasUsuarios: number = 0;
  private readonly storageKeyUsuarios = 'configuracionSistemaUsuariosEstado';

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.restaurarEstadoUsuarios();
    this.cargarConfiguracion();
    this.cargarUsuarios();
  }

  cargarConfiguracion(): void {
    this.cargando = true;
    this.endpointsService.obtenerLimiteTurno().subscribe({
      next: (limite) => {
        this.limiteTurno = limite;
        this.limiteTurnoOriginal = limite;
        this.cargando = false;
      },
      error: (error) => {
        console.error('Error al cargar la configuración:', error);
        showErrorToast('No se pudo cargar la configuración del sistema');
        this.cargando = false;
      }
    });
  }

  guardarConfiguracion(): void {
    if (this.limiteTurno < 1) {
      Swal.fire({
        icon: 'warning',
        title: 'Valor inválido',
        text: 'El límite debe ser al menos 1'
      });
      return;
    }

    Swal.fire({
      title: '¿Confirmar cambio?',
      text: `El límite de alumnos por turno se cambiará de ${this.limiteTurnoOriginal} a ${this.limiteTurno}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Guardar',
      cancelButtonText: 'Cancelar'
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.actualizarLimiteTurno(this.limiteTurno).subscribe({
          next: () => {
            this.limiteTurnoOriginal = this.limiteTurno;
            showSuccessToast('La configuración se ha actualizado correctamente');
          },
          error: (error) => {
            console.error('Error al guardar la configuración:', error);
            showErrorToast('No se pudo guardar la configuración');
          }
        });
      }
    });
  }

  hayCambios(): boolean {
    return this.limiteTurno !== this.limiteTurnoOriginal;
  }

  restaurarValor(): void {
    this.limiteTurno = this.limiteTurnoOriginal;
  }

  // === Gestión de roles (integrada) ===
  cargarUsuarios(): void {
    this.cargandoRoles = true;
    this.endpointsService.obtenerUsuarios().subscribe({
      next: (usuarios) => {
        this.usuarios = usuarios;
        this.inicializarRoles();
        this.actualizarPaginacionUsuarios();
        this.cargandoRoles = false;
      },
      error: () => {
        this.cargandoRoles = false;
        showErrorToast('No se pudieron cargar los usuarios.');
      },
    });
  }

  inicializarRoles(): void {
    this.usuarios.forEach((usuario) => {
      const roles = this.parseRoles(usuario.rol);
      this.rolesSeleccionados.set(usuario.id, roles);
    });
  }

  get usuariosFiltrados(): Usuario[] {
    const filtro = this.normalizarTexto(this.filtroUsuarios);
    if (!filtro) {
      return this.usuarios;
    }

    return this.usuarios.filter((usuario) => {
      const nombreCompleto = this.normalizarTexto(`${usuario.nombre} ${usuario.apellidos}`);
      const email = this.normalizarTexto(usuario.email);
      return nombreCompleto.includes(filtro) || email.includes(filtro);
    });
  }

  get usuariosPaginados(): Usuario[] {
    const start = (this.paginaActualUsuarios - 1) * this.tamanoPaginaUsuarios;
    const end = start + this.tamanoPaginaUsuarios;
    return this.usuariosFiltrados.slice(start, end);
  }

  cambiarPaginaUsuarios(pageNumber: number): void {
    if (this.cargandoRoles || pageNumber === this.paginaActualUsuarios) {
      return;
    }
    if (pageNumber < 1 || pageNumber > this.totalPaginasUsuarios) {
      return;
    }
    this.paginaActualUsuarios = pageNumber;
    this.guardarEstadoUsuarios();
  }

  private actualizarPaginacionUsuarios(): void {
    this.totalPaginasUsuarios = Math.ceil(this.usuariosFiltrados.length / this.tamanoPaginaUsuarios);
    if (this.totalPaginasUsuarios === 0) {
      this.paginaActualUsuarios = 1;
    } else if (this.paginaActualUsuarios > this.totalPaginasUsuarios) {
      this.paginaActualUsuarios = this.totalPaginasUsuarios;
    }
    this.guardarEstadoUsuarios();
  }

  parseRoles(rolString: string): RoleSelection {
    const cleanString = rolString.replaceAll(/[[\]]/g, '');
    const rolesArray = cleanString.split(',').map((r) => r.trim());

    return {
      ROLE_USER: rolesArray.includes('ROLE_USER'),
      ROLE_MANAGER: rolesArray.includes('ROLE_MANAGER'),
      ROLE_ADMIN: rolesArray.includes('ROLE_ADMIN'),
    };
  }

  onRoleChange(userId: number, role: keyof RoleSelection): void {
    const roles = this.rolesSeleccionados.get(userId);
    if (roles) {
      if (!roles.ROLE_USER && !roles.ROLE_MANAGER && !roles.ROLE_ADMIN) {
        roles[role] = true; // mantener al menos un rol
        Swal.fire({
          title: 'Rol requerido',
          text: 'Un usuario debe tener al menos un rol asignado.',
          icon: 'warning',
          timer: 2000,
          showConfirmButton: false,
        });
      }
    }
  }

  guardarCambios(usuario: Usuario): void {
    const roles = this.rolesSeleccionados.get(usuario.id);
    if (!roles) return;

    const rolesArray: string[] = [];
    if (roles.ROLE_USER) rolesArray.push('ROLE_USER');
    if (roles.ROLE_MANAGER) rolesArray.push('ROLE_MANAGER');
    if (roles.ROLE_ADMIN) rolesArray.push('ROLE_ADMIN');

    Swal.fire({
      title: '¿Estás seguro?',
      text: `Se actualizarán los roles de ${usuario.nombre} ${usuario.apellidos}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, actualizar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.actualizarRolesUsuario(usuario.id, rolesArray).subscribe({
          next: () => {
            showSuccessToast('Los roles del usuario se han actualizado correctamente.');
            this.cargarUsuarios();
          },
          error: () => {
            showErrorToast('No se pudieron actualizar los roles del usuario.');
          },
        });
      }
    });
  }

  resetearContrasena(usuario: Usuario): void {
    if (this.esCuentaGoogle(usuario)) {
      showErrorToast('Las cuentas de Google no permiten restablecer contraseña.');
      return;
    }

    Swal.fire({
      title: 'Restablecer contraseña',
      html: `
        <div style="position: relative; margin: 0.5rem 0 0.75rem;">
          <input id="nueva-contrasena" type="password" class="swal2-input" placeholder="Nueva contraseña" style="margin: 0; padding-right: 3rem;">
          <button type="button" class="taemoi-password-toggle" data-target="nueva-contrasena" aria-label="Mostrar contraseña"
            style="position: absolute; top: 50%; right: 0.85rem; transform: translateY(-50%); border: none; background: transparent; color: #6c757d; font-size: 1.2rem; cursor: pointer;">
            <i class="bi bi-eye"></i>
          </button>
        </div>
        <div style="text-align: left; margin: 0.5rem 0 0.75rem; font-size: 0.85rem;">
          <div style="font-weight: 600; margin-bottom: 0.35rem;">Requisitos:</div>
          <ul style="list-style: none; padding: 0; margin: 0; display: grid; gap: 0.3rem;">
            <li id="rule-length" style="display: flex; align-items: center; gap: 0.5rem;">
              <i id="rule-length-icon" class="bi bi-x-circle"></i>
              Mínimo 8 caracteres
            </li>
            <li id="rule-upper" style="display: flex; align-items: center; gap: 0.5rem;">
              <i id="rule-upper-icon" class="bi bi-x-circle"></i>
              Al menos 1 mayúscula
            </li>
            <li id="rule-lower" style="display: flex; align-items: center; gap: 0.5rem;">
              <i id="rule-lower-icon" class="bi bi-x-circle"></i>
              Al menos 1 minúscula
            </li>
            <li id="rule-number" style="display: flex; align-items: center; gap: 0.5rem;">
              <i id="rule-number-icon" class="bi bi-x-circle"></i>
              Al menos 1 número
            </li>
            <li id="rule-match" style="display: flex; align-items: center; gap: 0.5rem;">
              <i id="rule-match-icon" class="bi bi-x-circle"></i>
              Coinciden
            </li>
          </ul>
        </div>
        <div style="position: relative; margin: 0.75rem 0 0.5rem;">
          <input id="confirmar-contrasena" type="password" class="swal2-input" placeholder="Confirmar contraseña" style="margin: 0; padding-right: 3rem;">
          <button type="button" class="taemoi-password-toggle" data-target="confirmar-contrasena" aria-label="Mostrar contraseña"
            style="position: absolute; top: 50%; right: 0.85rem; transform: translateY(-50%); border: none; background: transparent; color: #6c757d; font-size: 1.2rem; cursor: pointer;">
            <i class="bi bi-eye"></i>
          </button>
        </div>
      `,
      focusConfirm: false,
      showCancelButton: true,
      confirmButtonText: 'Guardar',
      cancelButtonText: 'Cancelar',
      didOpen: () => {
        const nuevaInput = document.getElementById('nueva-contrasena') as HTMLInputElement | null;
        const confirmarInput = document.getElementById('confirmar-contrasena') as HTMLInputElement | null;
        const toggleButtons = document.querySelectorAll('.taemoi-password-toggle');
        let mostrarContrasena = false;

        const setRule = (id: string, ok: boolean) => {
          const item = document.getElementById(id);
          const icon = document.getElementById(`${id}-icon`);
          if (item) {
            item.style.color = ok ? '#1b7f3c' : '#b02a37';
          }
          if (icon) {
            icon.className = `bi ${ok ? 'bi-check-circle-fill' : 'bi-x-circle'}`;
          }
        };

        const actualizar = () => {
          const nueva = nuevaInput?.value || '';
          const confirmar = confirmarInput?.value || '';
          setRule('rule-length', nueva.length >= 8);
          setRule('rule-upper', /[A-Z]/.test(nueva));
          setRule('rule-lower', /[a-z]/.test(nueva));
          setRule('rule-number', /\d/.test(nueva));
          setRule('rule-match', nueva.length > 0 && nueva === confirmar);
        };

        nuevaInput?.addEventListener('input', actualizar);
        confirmarInput?.addEventListener('input', actualizar);
        actualizar();

        const actualizarVisibilidad = () => {
          if (nuevaInput) {
            nuevaInput.type = mostrarContrasena ? 'text' : 'password';
          }
          if (confirmarInput) {
            confirmarInput.type = mostrarContrasena ? 'text' : 'password';
          }
          toggleButtons.forEach((button) => {
            const icon = button.querySelector('i');
            if (icon) {
              icon.className = mostrarContrasena ? 'bi bi-eye-slash' : 'bi bi-eye';
            }
          });
        };

        toggleButtons.forEach((button) => {
          button.addEventListener('click', () => {
            mostrarContrasena = !mostrarContrasena;
            actualizarVisibilidad();
          });
        });
      },
      preConfirm: () => {
        const nueva = (document.getElementById('nueva-contrasena') as HTMLInputElement | null)?.value || '';
        const confirmar = (document.getElementById('confirmar-contrasena') as HTMLInputElement | null)?.value || '';
        if (!this.contrasenaCumpleReglas(nueva)) {
          Swal.showValidationMessage('La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).');
          return;
        }
        if (nueva !== confirmar) {
          Swal.showValidationMessage('Las contraseñas no coinciden');
          return;
        }
        return nueva;
      },
    }).then((result) => {
      if (result.isConfirmed && result.value) {
        this.endpointsService.actualizarContrasenaUsuario(usuario.id, result.value).subscribe({
          next: () => {
            showSuccessToast('Contraseña actualizada correctamente.');
          },
          error: (error) => {
            const mensaje = error?.error?.message || 'No se pudo actualizar la contraseña.';
            showErrorToast(mensaje);
          },
        });
      }
    });
  }

  esCuentaGoogle(usuario: Usuario): boolean {
    return usuario.authProvider === 'GOOGLE';
  }

  private contrasenaCumpleReglas(contrasena: string): boolean {
    return contrasena.length >= 8
      && /[A-Z]/.test(contrasena)
      && /[a-z]/.test(contrasena)
      && /\d/.test(contrasena);
  }

  getRoleBadgeClass(role: string): string {
    switch (role) {
      case 'ROLE_ADMIN':
        return 'badge-admin';
      case 'ROLE_MANAGER':
        return 'badge-manager';
      case 'ROLE_USER':
        return 'badge-user';
      default:
        return '';
    }
  }

  getRoleDisplayName(role: string): string {
    switch (role) {
      case 'ROLE_ADMIN':
        return 'Administrador';
      case 'ROLE_MANAGER':
        return 'Manager';
      case 'ROLE_USER':
        return 'Usuario';
      default:
        return role;
    }
  }

  getCurrentRoles(usuario: Usuario): string[] {
    const roles = this.rolesSeleccionados.get(usuario.id);
    if (!roles) return [];

    const rolesArray: string[] = [];
    if (roles.ROLE_ADMIN) rolesArray.push('ROLE_ADMIN');
    if (roles.ROLE_MANAGER) rolesArray.push('ROLE_MANAGER');
    if (roles.ROLE_USER) rolesArray.push('ROLE_USER');

    return rolesArray;
  }

  hasChanges(usuario: Usuario): boolean {
    const currentRoles = this.rolesSeleccionados.get(usuario.id);
    if (!currentRoles) return false;

    const originalRoles = this.parseRoles(usuario.rol);

    return (
      currentRoles.ROLE_USER !== originalRoles.ROLE_USER ||
      currentRoles.ROLE_MANAGER !== originalRoles.ROLE_MANAGER ||
      currentRoles.ROLE_ADMIN !== originalRoles.ROLE_ADMIN
    );
  }

  private guardarEstadoUsuarios(): void {
    const estado = {
      paginaActualUsuarios: this.paginaActualUsuarios,
    };
    sessionStorage.setItem(this.storageKeyUsuarios, JSON.stringify(estado));
  }

  private restaurarEstadoUsuarios(): void {
    const estadoGuardado = sessionStorage.getItem(this.storageKeyUsuarios);
    if (!estadoGuardado) {
      return;
    }
    try {
      const estado = JSON.parse(estadoGuardado);
      this.paginaActualUsuarios = estado.paginaActualUsuarios || 1;
    } catch (error) {
      console.error('Error parsing saved pagination state:', error);
    }
  }

  aplicarFiltroUsuarios(): void {
    this.paginaActualUsuarios = 1;
    this.actualizarPaginacionUsuarios();
  }

  private normalizarTexto(valor: string): string {
    return (valor || '')
      .toString()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }
}
