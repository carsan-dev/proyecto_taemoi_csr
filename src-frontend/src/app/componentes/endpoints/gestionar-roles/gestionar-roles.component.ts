import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';

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
  selector: 'app-gestionar-roles',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestionar-roles.component.html',
  styleUrls: ['./gestionar-roles.component.scss'],
})
export class GestionarRolesComponent implements OnInit {
  usuarios: Usuario[] = [];
  cargando: boolean = true;
  rolesSeleccionados: Map<number, RoleSelection> = new Map();

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.cargando = true;
    this.endpointsService.obtenerUsuarios().subscribe({
      next: (usuarios) => {
        this.usuarios = usuarios;
        this.inicializarRoles();
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los usuarios.',
          icon: 'error',
        });
      },
    });
  }

  inicializarRoles(): void {
    this.usuarios.forEach((usuario) => {
      const roles = this.parseRoles(usuario.rol);
      this.rolesSeleccionados.set(usuario.id, roles);
    });
  }

  parseRoles(rolString: string): RoleSelection {
    // The rol string comes as "[ROLE_USER, ROLE_ADMIN]" format
    const cleanString = rolString.replace(/[\[\]]/g, '');
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
      // NgModel has already toggled the value, so we just check if all are now false
      // If all roles are false, revert the change
      if (!roles.ROLE_USER && !roles.ROLE_MANAGER && !roles.ROLE_ADMIN) {
        roles[role] = true; // Revert the change to keep at least one role
        Swal.fire({
          title: 'Rol requerido',
          text: 'Un usuario debe tener al menos un rol asignado.',
          icon: 'warning',
          timer: 2000,
          showConfirmButton: false
        });
      }
    }
  }

  guardarCambios(usuario: Usuario): void {
    const roles = this.rolesSeleccionados.get(usuario.id);
    if (!roles) return;

    // Build array of selected roles
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
            Swal.fire({
              title: '¡Actualizado!',
              text: 'Los roles del usuario se han actualizado correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.cargarUsuarios();
          },
          error: () => {
            Swal.fire({
              title: 'Error',
              text: 'No se pudieron actualizar los roles del usuario.',
              icon: 'error',
            });
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
}
