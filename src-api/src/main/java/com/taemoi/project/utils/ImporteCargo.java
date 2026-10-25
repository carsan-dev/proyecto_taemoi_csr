package com.taemoi.project.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.exceptions.PrecioNoValidoException;

/**
 * Decisión 297: el importe de un cargo ({@code producto_alumno}) es precio × cantidad, con el precio nulo como 0 y la
 * cantidad nula o menor que 1 como 1. Único punto de verdad: el cobro, la caja, la tesorería, lo vencido, las
 * solicitudes y los informes usan {@link #de} en Java y {@link #SQL} en las consultas (JPQL y SQL nativo, con el cargo
 * como {@code pa}). El precio del cargo sigue siendo el unitario.
 */
public final class ImporteCargo {

	/**
	 * La misma regla en una consulta, con el cargo como {@code pa}; los nombres valen en JPQL (atributos) y en SQL nativo
	 * (columnas). Sin redondear: quien la usa redondea (o suma) como antes con {@code COALESCE(pa.precio, 0)}.
	 */
	public static final String SQL = "(COALESCE(pa.precio, 0) * "
			+ "(CASE WHEN pa.cantidad IS NULL OR pa.cantidad < 1 THEN 1 ELSE pa.cantidad END))";

	private ImporteCargo() {
	}

	/** La cantidad que cuenta para el importe: la del cargo, o 1 si es nula o menor que 1. */
	public static int cantidad(Integer cantidad) {
		return cantidad == null || cantidad < 1 ? 1 : cantidad;
	}

	/**
	 * Precio × cantidad sin redondear, como {@link #SQL}: para las sumas en {@code double} de la tesorería y los informes,
	 * que antes sumaban {@code COALESCE(precio, 0)} tal cual (con cantidad 1 dan lo mismo que entonces).
	 */
	public static double sinRedondear(Double precio, Integer cantidad) {
		return (precio != null ? precio : 0.0) * cantidad(cantidad);
	}

	/** {@link #sinRedondear(Double, Integer)} del cargo. */
	public static double sinRedondear(ProductoAlumno cargo) {
		return sinRedondear(cargo.getPrecio(), cargo.getCantidad());
	}

	/** Precio × cantidad, con dos decimales (HALF_UP sobre el producto). */
	public static BigDecimal de(Double precio, Integer cantidad) {
		BigDecimal unitario = BigDecimal.valueOf(precio != null ? precio : 0.0);
		return unitario.multiply(BigDecimal.valueOf(cantidad(cantidad))).setScale(2, RoundingMode.HALF_UP);
	}

	/** El importe del cargo: precio × cantidad, con dos decimales. */
	public static BigDecimal de(ProductoAlumno cargo) {
		return de(cargo.getPrecio(), cargo.getCantidad());
	}

	// ------------------------------------------------------------------ precio válido (decisión 297, ronda 2)

	public static final String PRECIO_NEGATIVO = "El precio no puede ser negativo.";
	public static final String PRECIO_CON_MAS_DE_DOS_DECIMALES = "El precio no puede tener más de 2 decimales.";
	public static final String PRECIO_NO_VALIDO = "El precio no es un número válido.";

	/**
	 * Único punto de verdad del precio que se fija a un cargo o a un producto del catálogo, y de la cuantía personalizada
	 * de una inscripción ({@code alumno_deporte.cuantia_tarifa}, que pasa tal cual al precio de las mensualidades): nulo (sin precio, cuenta como
	 * 0) o un número finito, no negativo y con 2 decimales como mucho. Con más decimales los cobros (que redondean por
	 * cargo) y los totales (que suman sin redondear) descuadran en céntimos. Se compara sobre la representación decimal
	 * más corta del {@code double} ({@link BigDecimal#valueOf(double)}), así que 0,07 o 1,1 valen aunque en binario no
	 * sean exactos.
	 *
	 * @throws PrecioNoValidoException ({@link IllegalArgumentException}: 400 con {@code mensaje}) si no vale
	 */
	public static void exigirPrecioValido(Double precio) {
		if (precio == null) {
			return;
		}
		if (precio.isNaN() || precio.isInfinite()) {
			throw new PrecioNoValidoException(PRECIO_NO_VALIDO);
		}
		if (precio < 0) {
			throw new PrecioNoValidoException(PRECIO_NEGATIVO);
		}
		if (BigDecimal.valueOf(precio).stripTrailingZeros().scale() > 2) {
			throw new PrecioNoValidoException(PRECIO_CON_MAS_DE_DOS_DECIMALES);
		}
	}

	/**
	 * {@link #exigirPrecioValido} solo si el precio cambia ({@code nuevo} no nulo y distinto de {@code actual}): un precio
	 * antiguo con más decimales que se reenvía sin tocar no impide editar lo demás del cargo o del producto.
	 */
	public static void exigirPrecioValidoSiCambia(Double nuevo, Double actual) {
		if (nuevo != null && (actual == null || Double.compare(nuevo, actual) != 0)) {
			exigirPrecioValido(nuevo);
		}
	}
}
