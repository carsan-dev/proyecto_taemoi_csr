import { TestBed } from '@angular/core/testing';

import { Catalogo } from './catalogo.models';
import { CatalogoService } from './catalogo.service';
import { proveerCatalogoTesting } from './testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from './testing/catalogo.fixtures';

describe('CatalogoService: lecturas por disciplina que usan los componentes', () => {
  function servicio(catalogo: Catalogo | null): CatalogoService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(catalogo)] });
    return TestBed.inject(CatalogoService);
  }

  it('Taemoi: primera disciplina activa por orden y disciplinas con grados', () => {
    const s = servicio(CATALOGO_TAEMOI);
    expect(s.primeraDisciplinaActiva()).toBe('TAEKWONDO');
    expect(s.disciplinasConGrados().map((d) => d.codigo)).toEqual(['TAEKWONDO', 'KICKBOXING']);
  });

  it('Taemoi: flags de grado, licencia, competición y material de examen por código', () => {
    const s = servicio(CATALOGO_TAEMOI);
    const tabla: [string, boolean, boolean, boolean, boolean][] = [
      // codigo, tieneGrados, requiereLicencia, permiteCompeticion, tieneMaterialExamen
      ['TAEKWONDO', true, true, true, true],
      ['KICKBOXING', true, true, true, true],
      ['PILATES', false, false, false, false],
      ['DEFENSA_PERSONAL_FEMENINA', false, false, false, false],
    ];
    tabla.forEach(([codigo, grados, licencia, competicion, material]) => {
      expect(s.tieneGrados(codigo)).withContext(codigo).toBe(grados);
      expect(s.sinGrados(codigo)).withContext(codigo).toBe(!grados);
      expect(s.requiereLicencia(codigo)).withContext(codigo).toBe(licencia);
      expect(s.permiteCompeticion(codigo)).withContext(codigo).toBe(competicion);
      expect(s.tieneMaterialExamen(codigo)).withContext(codigo).toBe(material);
    });
  });

  it('un código desconocido no cuenta como «sin grados» ni activa licencia o competición', () => {
    const s = servicio(CATALOGO_TAEMOI);
    expect(s.sinGrados('OTRO')).toBeFalse();
    expect(s.tieneGrados('OTRO')).toBeFalse();
    expect(s.requiereLicencia(null)).toBeFalse();
    expect(s.permiteCompeticion(undefined)).toBeFalse();
    expect(s.tieneMaterialExamen('OTRO')).toBeFalse();
  });

  it('Taemoi: emoji y color de D12 por código; desconocido, neutro', () => {
    const s = servicio(CATALOGO_TAEMOI);
    expect(s.emojiDisciplina('KICKBOXING')).toBe('🥊');
    expect(s.colorDisciplina('KICKBOXING')).toBe('#e74c3c');
    expect(s.emojiDisciplina('JUDO')).toBe('⭐');
    expect(s.colorDisciplina('JUDO')).toBe('#6c757d');
  });

  it('segunda escuela: JUDO por orden, con grados, sin licencia, con competición; emoji y color neutros', () => {
    const s = servicio(CATALOGO_SEGUNDA_ESCUELA);
    expect(s.primeraDisciplinaActiva()).toBe('JUDO');
    expect(s.disciplinasConGrados().map((d) => d.codigo)).toEqual(['JUDO']);
    expect(s.tieneGrados('JUDO')).toBeTrue();
    expect(s.requiereLicencia('JUDO')).toBeFalse();
    expect(s.permiteCompeticion('JUDO')).toBeTrue();
    expect(s.sinGrados('YOGA')).toBeTrue();
    expect(s.emojiDisciplina('JUDO')).toBe('⭐');
    expect(s.colorDisciplina('JUDO')).toBe('#6c757d');
    // Los códigos de Taemoi no se reconocen ni heredan los valores de Taemoi.
    expect(s.emojiDisciplina('TAEKWONDO')).toBe('⭐');
    expect(s.tieneGrados('TAEKWONDO')).toBeFalse();
  });

  it('sin catálogo cargado (D15) todo queda vacío', () => {
    const s = servicio(null);
    expect(s.primeraDisciplinaActiva()).toBe('');
    expect(s.disciplinasConGrados()).toEqual([]);
    expect(s.requiereLicencia('TAEKWONDO')).toBeFalse();
    expect(s.emojiDisciplina('TAEKWONDO')).toBe('⭐');
  });
});
