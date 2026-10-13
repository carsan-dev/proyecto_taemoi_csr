package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.OrganizacionUsuarioDTO;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.MembresiaService;

@Service
public class MembresiaServiceImpl implements MembresiaService {

	private final MembresiaRepository membresias;
	private final OrganizacionRepository organizaciones;
	private final UsuarioRepository usuarios;
	private final AlumnoRepository alumnos;
	private final ResolutorOrganizacion resolutor;

	public MembresiaServiceImpl(MembresiaRepository membresias, OrganizacionRepository organizaciones,
			UsuarioRepository usuarios, AlumnoRepository alumnos, ResolutorOrganizacion resolutor) {
		this.membresias = membresias;
		this.organizaciones = organizaciones;
		this.usuarios = usuarios;
		this.alumnos = alumnos;
		this.resolutor = resolutor;
	}

	@Override
	public Set<Roles> autoridadesEn(Long usuarioId, Long organizacionId) {
		if (usuarioId == null || organizacionId == null) {
			return EnumSet.noneOf(Roles.class);
		}
		return RolMembresia.autoridades(membresias.findRolesActivos(usuarioId, organizacionId));
	}

	@Override
	public List<Long> organizacionesConMembresiaActiva(Long usuarioId) {
		return List.copyOf(autoridadesPorOrganizacion(usuarioId).keySet());
	}

	@Override
	public boolean puedeEntrarEn(Usuario usuario, Long organizacionId) {
		return puedeEntrarCon(usuario, organizacionId, autoridadesEn(usuario.getId(), organizacionId));
	}

	@Override
	public Optional<Long> elegirOrganizacionParaLogin(Usuario usuario) {
		List<Long> validas = new ArrayList<>();
		autoridadesPorOrganizacion(usuario.getId()).forEach((organizacionId, autoridades) -> {
			if (puedeEntrarCon(usuario, organizacionId, autoridades)) {
				validas.add(organizacionId);
			}
		});
		if (validas.isEmpty()) {
			return Optional.empty();
		}
		if (validas.size() == 1) {
			return Optional.of(validas.get(0));
		}
		Long ultima = usuario.getUltimaOrganizacionId();
		if (ultima != null && validas.contains(ultima)) {
			return Optional.of(ultima);
		}
		return validas.stream().min(Comparator.naturalOrder());
	}

	@Override
	@Transactional
	public void registrarUltimaOrganizacion(Usuario usuario, Long organizacionId) {
		if (Objects.equals(usuario.getUltimaOrganizacionId(), organizacionId)) {
			return;
		}
		usuarios.actualizarUltimaOrganizacion(usuario.getId(), organizacionId);
		usuario.setUltimaOrganizacionId(organizacionId);
	}

	@Override
	public List<OrganizacionUsuarioDTO> organizacionesDe(Long usuarioId, Long organizacionActiva) {
		Map<Long, Set<Roles>> porOrganizacion = autoridadesPorOrganizacion(usuarioId);
		Map<Long, Organizacion> datos = new HashMap<>();
		organizaciones.findAllById(porOrganizacion.keySet()).forEach(o -> datos.put(o.getId(), o));
		List<OrganizacionUsuarioDTO> resultado = new ArrayList<>();
		porOrganizacion.forEach((id, autoridades) -> {
			Organizacion org = datos.get(id);
			if (org != null) {
				resultado.add(new OrganizacionUsuarioDTO(id, org.getSlug(), org.getNombre(),
						autoridades.stream().map(Enum::name).sorted().toList(), id.equals(organizacionActiva)));
			}
		});
		return resultado;
	}

	@Override
	@Transactional
	public Set<Roles> reemplazarAutoridades(Long usuarioId, Long organizacionId, Set<Roles> autoridades) {
		Set<Roles> deseadas = autoridades == null || autoridades.isEmpty()
				? EnumSet.noneOf(Roles.class)
				: EnumSet.copyOf(autoridades);
		List<Membresia> actuales = new ArrayList<>(
				membresias.findByUsuarioIdAndOrganizacionId(usuarioId, organizacionId));

		Set<Roles> cubiertas = EnumSet.noneOf(Roles.class);
		for (Membresia membresia : actuales) {
			if (!membresia.isActiva()) {
				continue;
			}
			if (deseadas.contains(membresia.getRol().autoridad())) {
				cubiertas.add(membresia.getRol().autoridad());
			} else {
				membresia.setActiva(false);
			}
		}
		for (Roles autoridad : deseadas) {
			if (cubiertas.contains(autoridad)) {
				continue;
			}
			RolMembresia canonico = RolMembresia.desdeAutoridad(autoridad);
			Membresia existente = actuales.stream()
					.filter(m -> m.getRol() == canonico)
					.findFirst()
					.orElse(null);
			if (existente != null) {
				existente.setActiva(true);
			} else {
				actuales.add(new Membresia(usuarioId, organizacionId, canonico));
			}
		}
		membresias.saveAll(actuales);
		membresias.flush();
		return autoridadesEn(usuarioId, organizacionId);
	}

	@Override
	@Transactional
	public void asegurarMembresia(Long usuarioId, Long organizacionId, RolMembresia rol) {
		Objects.requireNonNull(usuarioId, "usuarioId");
		Objects.requireNonNull(organizacionId, "organizacionId");
		Objects.requireNonNull(rol, "rol");
		Membresia membresia = membresias.findByUsuarioIdAndOrganizacionId(usuarioId, organizacionId).stream()
				.filter(m -> m.getRol() == rol)
				.findFirst()
				.orElseGet(() -> new Membresia(usuarioId, organizacionId, rol));
		membresia.setActiva(true);
		membresias.save(membresia);
	}

	@Override
	public Map<Long, Set<Roles>> autoridadesPorUsuario(Long organizacionId) {
		Map<Long, Set<Roles>> resultado = new HashMap<>();
		for (Membresia membresia : membresias.findByOrganizacionId(organizacionId)) {
			if (membresia.isActiva()) {
				resultado.computeIfAbsent(membresia.getUsuarioId(), k -> EnumSet.noneOf(Roles.class))
						.add(membresia.getRol().autoridad());
			}
		}
		return resultado;
	}

	@Override
	public List<Usuario> usuariosDeOrganizacion(Long organizacionId) {
		return membresias.findUsuariosDeOrganizacion(organizacionId, esPorDefecto(organizacionId));
	}

	@Override
	public boolean esUsuarioDeOrganizacion(Long usuarioId, Long organizacionId) {
		if (membresias.existsByUsuarioIdAndOrganizacionId(usuarioId, organizacionId)) {
			return true;
		}
		return esPorDefecto(organizacionId) && !membresias.existsByUsuarioId(usuarioId);
	}

	@Override
	public boolean puedeGestionarUsuarioGlobal(Long usuarioId, Long organizacionId) {
		if (usuarioId == null || organizacionId == null) {
			return false;
		}
		List<Membresia> todas = membresias.findByUsuarioId(usuarioId);
		if (todas.isEmpty()) {
			return esPorDefecto(organizacionId);
		}
		return todas.stream().allMatch(m -> organizacionId.equals(m.getOrganizacionId()));
	}

	@Override
	@Transactional
	public boolean retirarFamilia(Long usuarioId, Long organizacionId) {
		membresias.findByUsuarioIdAndOrganizacionId(usuarioId, organizacionId).stream()
				.filter(m -> m.getRol() == RolMembresia.FAMILIA)
				.forEach(membresias::delete);
		membresias.flush();
		return membresias.existsByUsuarioId(usuarioId);
	}

	private boolean esPorDefecto(Long organizacionId) {
		return resolutor.getOrganizacionPorDefecto().equals(organizacionId);
	}

	private boolean puedeEntrarCon(Usuario usuario, Long organizacionId, Set<Roles> autoridades) {
		if (autoridades.isEmpty()) {
			return false;
		}
		if (!RolMembresia.requiereAlumnoActivo(autoridades)) {
			return true;
		}
		return alumnos.contarActivosPorEmailEnOrganizacion(usuario.getEmail(), organizacionId) > 0;
	}

	/** Autoridades por organización (ordenadas por id) a partir de las membresías activas. */
	private Map<Long, Set<Roles>> autoridadesPorOrganizacion(Long usuarioId) {
		Map<Long, Set<Roles>> resultado = new TreeMap<>();
		for (Membresia membresia : membresias.findActivasDeUsuario(usuarioId)) {
			resultado.computeIfAbsent(membresia.getOrganizacionId(), k -> EnumSet.noneOf(Roles.class))
					.add(membresia.getRol().autoridad());
		}
		return new LinkedHashMap<>(resultado);
	}
}
