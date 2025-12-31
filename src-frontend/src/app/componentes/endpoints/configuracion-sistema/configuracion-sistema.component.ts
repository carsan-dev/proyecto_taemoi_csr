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

  get usuariosPaginados(): Usuario[] {
    const start = (this.paginaActualUsuarios - 1) * this.tamanoPaginaUsuarios;
    const end = start + this.tamanoPaginaUsuarios;
    return this.usuarios.slice(start, end);
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
    this.totalPaginasUsuarios = Math.ceil(this.usuarios.length / this.tamanoPaginaUsuarios);
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
}
