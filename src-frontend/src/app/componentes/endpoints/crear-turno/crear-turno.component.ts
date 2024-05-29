import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-turno',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-turno.component.html',
  styleUrl: './crear-turno.component.scss',
})
export class CrearTurnoComponent implements OnInit {
  turnoForm!: FormGroup;
  grupos: any[] = [];

  constructor(
    private fb: FormBuilder,
    private endpointsService: EndpointsService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.obtenerGrupos();
  }

  initForm(): void {
    this.turnoForm = this.fb.group({
      diaSemana: ['', Validators.required],
      horaInicio: ['', Validators.required],
      horaFin: ['', Validators.required],
      asignarGrupo: [false],
      grupoId: [''],
    });
  }

  obtenerGrupos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerTodosLosGrupos(token).subscribe({
        next: (response) => {
          this.grupos = response;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor para obtener los grupos',
            icon: 'error',
          });
        },
      });
    }
  }

  crearTurno(): void {
    const token = localStorage.getItem('token');
    const turnoForm = this.turnoForm.value;

    if (token) {
      const turnoRequest = turnoForm.asignarGrupo
        ? this.endpointsService.crearTurnoConGrupo(turnoForm, token)
        : this.endpointsService.crearTurnoSinGrupo(turnoForm, token);

      turnoRequest.subscribe({
        next: (response) => {
          Swal.fire({
            title: 'Perfecto!',
            text: 'Has creado un nuevo turno!',
            icon: 'success',
          });
          this.router.navigate(['/turnosListar']);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No has completado todos los campos requeridos',
            icon: 'error',
          });
        },
        complete: () => {},
      });
    }
  }
}
