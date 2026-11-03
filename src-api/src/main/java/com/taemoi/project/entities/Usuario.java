package com.taemoi.project.entities;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Usuario implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre no puede estar en blanco")
	@Size(max = 50, message = "El nombre no puede tener más de 50 caracteres")
	private String nombre;

	@NotBlank(message = "Los apellidos no pueden estar en blanco")
	@Size(max = 50, message = "Los apellidos no pueden tener más de 50 caracteres")
	private String apellidos;

	@Column(unique = true)
	@Email(message = "La dirección de correo electrónico debe ser válida")
	@NotBlank(message = "El correo electrónico no puede estar en blanco")
	private String email;

	/**
	 * Hash de la contraseña. NULL solo en una invitación pendiente (pieza E8-4, V31_9, decisión 223): cuenta LOCAL que
	 * aún no ha creado su contraseña; el login la rechaza como una contraseña incorrecta. Una cadena vacía sigue sin
	 * poder guardarse ({@code @Size}); las reglas de la contraseña nueva están en los DTO de signup, registro y
	 * restablecer.
	 */
	@Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
	@JsonIgnore
	private String contrasena;

	@Enumerated(EnumType.STRING)
	@Column(name = "auth_provider")
	private AuthProvider authProvider = AuthProvider.LOCAL;

	@Column(name = "reset_token_hash", length = 64)
	@JsonIgnore
	private String resetTokenHash;

	@Column(name = "reset_token_expires_at")
	@JsonIgnore
	private LocalDateTime resetTokenExpiresAt;

	@Column(name = "recordatorio_racha_email_habilitado", nullable = false)
	private Boolean recordatorioRachaEmailHabilitado = false;

	@Column(name = "ultimo_recordatorio_racha_reset")
	private LocalDate ultimoRecordatorioRachaReset;

	// Los roles globales heredados (tabla usuario_rol) ya no se mapean (fase 3, entrega 1): los permisos viven en
	// Membresia por organización desde V20. La tabla sigue hasta V32; desde V31 su FK borra en cascada las filas
	// de un usuario borrado.

	/**
	 * Administrador de plataforma (V31): marca global, no una membresía. Solo se pone con SQL fuera del
	 * repositorio: sin setter, fuera del JSON y Hibernate nunca la escribe (al insertar vale el FALSE por
	 * defecto de la columna; al guardar un usuario cargado antes de marcarlo no se pisa la marca).
	 */
	@JsonIgnore
	@Column(name = "es_admin_plataforma", nullable = false, insertable = false, updatable = false)
	private Boolean esAdminPlataforma = Boolean.FALSE;

	/** Escuela elegida en el último inicio de sesión o cambio de escuela (V20). */
	@Column(name = "ultima_organizacion_id")
	private Long ultimaOrganizacionId;

	/*
	 * Pieza E10-2: la columna usuario.alumno_id ya no se mapea ni se escribe (sigue en la tabla hasta la V32, sin uso).
	 * El vínculo de la cuenta con sus fichas es usuario_alumno (VinculoFamiliaService y AccesoFamiliaService).
	 */

	/**
	 * Perfil de la cuenta (V31_14, decisión 322): nombre por el que quiere que la llamen y cumpleaños sin año (los dos
	 * valores o ninguno, {@code ck_usuario_cumple}). Datos personales fuera del JSON de la cuenta. Solo los cambia {@code PerfilCuentaService}, con sus reglas: sin {@code @Size} aquí
	 * porque el límite es de 40 puntos de código y {@code @Size} cuenta unidades UTF-16.
	 */
	@JsonIgnore
	@Column(name = "nombre_preferido", length = 40)
	private String nombrePreferido;

	@JsonIgnore
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "cumple_dia")
	private Integer cumpleDia;

	@JsonIgnore
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "cumple_mes")
	private Integer cumpleMes;

	/**
	 * Modo privado de importes (V31_18, decisión 419): el panel de esta cuenta abre con el dinero tapado. Es de la cuenta,
	 * no de la escuela, y FUERA del JSON por el mismo motivo que el perfil. Solo la cambia {@code PerfilCuentaService}.
	 */
	@JsonIgnore
	@Column(name = "importes_tapados", nullable = false)
	private boolean importesTapados;

	/**
	 * Siempre vacío: las autoridades dependen de la organización activa y las calcula
	 * {@code MembresiaService} a partir de las membresías (ver {@code JwtAuthenticationFilter}).
	 * No usar como fuente de permisos.
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.emptySet();
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	@JsonIgnore
	public String getPassword() {
		return contrasena;
	}

	/**
	 * Invitación pendiente (pieza E8-4, D8-13): cuenta LOCAL (o sin proveedor, como las antiguas) sin contraseña. No es
	 * una propiedad JSON.
	 */
	public boolean invitacionPendiente() {
		return contrasena == null && getAuthProvider() == AuthProvider.LOCAL;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

	/** Administrador de plataforma (V31). Aún no da ninguna autoridad (fase 3, entrega 3). */
	@JsonIgnore
	public boolean isEsAdminPlataforma() {
		return Boolean.TRUE.equals(esAdminPlataforma);
	}

	@JsonIgnore
	public Long getUltimaOrganizacionId() {
		return ultimaOrganizacionId;
	}

	public void setUltimaOrganizacionId(Long ultimaOrganizacionId) {
		this.ultimaOrganizacionId = ultimaOrganizacionId;
	}

	public AuthProvider getAuthProvider() {
		return authProvider == null ? AuthProvider.LOCAL : authProvider;
	}

	public void setAuthProvider(AuthProvider authProvider) {
		this.authProvider = authProvider;
	}

	public String getResetTokenHash() {
		return resetTokenHash;
	}

	public void setResetTokenHash(String resetTokenHash) {
		this.resetTokenHash = resetTokenHash;
	}

	public LocalDateTime getResetTokenExpiresAt() {
		return resetTokenExpiresAt;
	}

	public void setResetTokenExpiresAt(LocalDateTime resetTokenExpiresAt) {
		this.resetTokenExpiresAt = resetTokenExpiresAt;
	}

	public Boolean getRecordatorioRachaEmailHabilitado() {
		return recordatorioRachaEmailHabilitado;
	}

	public void setRecordatorioRachaEmailHabilitado(Boolean recordatorioRachaEmailHabilitado) {
		this.recordatorioRachaEmailHabilitado = Boolean.TRUE.equals(recordatorioRachaEmailHabilitado);
	}

	public LocalDate getUltimoRecordatorioRachaReset() {
		return ultimoRecordatorioRachaReset;
	}

	public void setUltimoRecordatorioRachaReset(LocalDate ultimoRecordatorioRachaReset) {
		this.ultimoRecordatorioRachaReset = ultimoRecordatorioRachaReset;
	}

	public String getNombrePreferido() {
		return nombrePreferido;
	}

	public void setNombrePreferido(String nombrePreferido) {
		this.nombrePreferido = nombrePreferido;
	}

	public Integer getCumpleDia() {
		return cumpleDia;
	}

	public void setCumpleDia(Integer cumpleDia) {
		this.cumpleDia = cumpleDia;
	}

	public Integer getCumpleMes() {
		return cumpleMes;
	}

	public void setCumpleMes(Integer cumpleMes) {
		this.cumpleMes = cumpleMes;
	}

	public boolean isImportesTapados() {
		return importesTapados;
	}

	public void setImportesTapados(boolean importesTapados) {
		this.importesTapados = importesTapados;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}
}
