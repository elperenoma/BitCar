package com.mycompany.bitcar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BitCarWeb {

    private static final Familia familia = new Familia();

    public static void main(String[] args) throws Exception {
        int puerto = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);

        server.createContext("/", BitCarWeb::manejarPeticion);
        server.createContext("/logo", BitCarWeb::servirLogo);
        server.setExecutor(null);
        server.start();

        String url = "http://localhost:" + puerto;
        System.out.println("====================================================");
        System.out.println("  BITCAR WEB INICIADO EN: " + url);
        System.out.println("====================================================");

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(url));
        }
    }

    private static void servirLogo(HttpExchange exchange) throws IOException {
        String[] rutas = {
            "logo.png", "logo.jpg", "logo.jpeg",
            "Logo.png", "Logo.jpg", "Logo.jpeg",
            "src/main/resources/logo.png", "src/main/resources/logo.jpg",
            "src/com/mycompany/bitcar/logo.png", "src/com/mycompany/bitcar/logo.jpg"
        };

        for (String ruta : rutas) {
            File archivo = new File(ruta);
            if (archivo.exists() && archivo.isFile()) {
                String nombre = ruta.toLowerCase(Locale.ROOT);
                String mime = nombre.endsWith(".png") ? "image/png" : "image/jpeg";
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
        }

        String[] recursos = {"/logo.png", "/logo.jpg", "/com/mycompany/bitcar/logo.png"};
        for (String rec : recursos) {
            try (InputStream is = BitCarWeb.class.getResourceAsStream(rec)) {
                if (is != null) {
                    String mime = rec.endsWith(".png") ? "image/png" : "image/jpeg";
                    byte[] bytes = is.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", mime);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }
            }
        }

        byte[] svgBytes = svgLogoRespaldo().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "image/svg+xml; charset=UTF-8");
        exchange.sendResponseHeaders(200, svgBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(svgBytes);
        }
    }

    private static void manejarPeticion(HttpExchange exchange) throws IOException {
        Map<String, String> params = leerParametros(exchange.getRequestURI().getRawQuery());
        String accion = params.getOrDefault("accion", "");
        String vista = params.getOrDefault("vista", "flota");

        // Caso de uso: Añadir miembro al grupo familiar
        if ("agregar".equals(accion)) {
            String dueno = params.getOrDefault("dueno", "").trim();
            String pat = params.getOrDefault("patente", "").trim().toUpperCase(Locale.ROOT);
            String marca = params.getOrDefault("marca", "").trim();
            String modelo = params.getOrDefault("modelo", "").trim();
            int km = parsearEntero(params.get("km"), 0);

            if (!dueno.isEmpty() && !pat.isEmpty()) {
                familia.AñadirMiembro(new Vehiculo(dueno, pat, marca, modelo, km));
                String ok = codificar("Nuevo miembro agregado: " + dueno + " (" + pat + ").");
                redirigir(exchange, "/?vista=modificar&subvista=agregar&msg=" + ok);
                return;
            }
        }

        // Caso de uso: Quitar miembro del grupo familiar
        if ("eliminar".equals(accion)) {
            String pat = params.getOrDefault("patente", "").trim();
            if (!pat.isEmpty()) {
                Vehiculo existente = familia.BuscarPorPatente(pat);
                familia.QuitarMiembro(pat);
                if (existente != null) {
                    String ok = codificar("Se eliminó el vehículo de: " + existente.nombre_dueno + ".");
                    redirigir(exchange, "/?vista=modificar&subvista=eliminar&msg=" + ok);
                } else {
                    String err = codificar("Vehículo no encontrado con patente: " + pat);
                    redirigir(exchange, "/?vista=modificar&subvista=eliminar&err=" + err);
                }
                return;
            }
        }

        // Caso de uso: Registrar revisión mecánica a un auto
        if ("mantencion".equals(accion)) {
            String pat = params.getOrDefault("patente", "").trim();
            String detalle = params.getOrDefault("detalle", "").trim();
            int nKm = parsearEntero(params.get("km"), 0);
            Vehiculo v = familia.BuscarPorPatente(pat);

            if (v != null) {
                v.registrarMantencion(detalle, nKm);
                String ok = codificar("Mantención y datos actualizados para " + v.patente + ".");
                redirigir(exchange, "/?vista=flota&msg=" + ok);
                return;
            } else {
                String err = codificar("Vehículo no encontrado con patente: " + pat);
                redirigir(exchange, "/?vista=revision&err=" + err);
                return;
            }
        }

        // Caso de uso Administrador: Agendar cita solo en su propio vehículo
        if ("agendar_cita".equals(accion)) {
            String servicio = params.getOrDefault("servicio", "").trim();
            String fecha = formatearFecha(params.getOrDefault("fecha", "").trim());
            String hora = params.getOrDefault("hora", "").trim();

            if (!servicio.isEmpty() && !fecha.isEmpty() && !hora.isEmpty()) {
                familia.administrador.AgendarCita(servicio, fecha, hora);
                String ok = codificar("Nueva cita agendada para " + familia.administrador.patente + ".");
                redirigir(exchange, "/?vista=citas&msg=" + ok);
                return;
            }
        }

        // Caso de uso Administrador: Marcar su propia cita como completada
        if ("completar_cita".equals(accion)) {
            int idx = parsearEntero(params.get("idx"), -1);
            if (idx >= 0 && idx < familia.administrador.citas.size()) {
                Cita c = familia.administrador.citas.get(idx);
                c.Completada();
                String ok = codificar("Cita marcada como completada: " + c.tipo_servicio + ".");
                redirigir(exchange, "/?vista=citas&msg=" + ok);
                return;
            }
        }

        // Caso de uso Administrador: Agregar reparación o repuesto pendiente solo en su vehículo
        if ("agregar_reparacion".equals(accion)) {
            String categoria = params.getOrDefault("categoria", "Reparacion").trim();
            String descripcion = params.getOrDefault("descripcion", "").trim();

            if (!descripcion.isEmpty()) {
                familia.administrador.AgregarReparacion(categoria, descripcion);
                String ok = codificar(categoria + " registrada en " + familia.administrador.patente + ".");
                redirigir(exchange, "/?vista=reparaciones&msg=" + ok);
                return;
            }
        }

        // Caso de uso Administrador: Marcar su propia reparación/repuesto como completada
        if ("completar_rep".equals(accion)) {
            int idx = parsearEntero(params.get("idx"), -1);
            if (idx >= 0 && idx < familia.administrador.reparaciones.size()) {
                Reparacion r = familia.administrador.reparaciones.get(idx);
                r.Completada();
                String ok = codificar(r.categoria + " marcada como completada: " + r.descripcion + ".");
                redirigir(exchange, "/?vista=reparaciones&msg=" + ok);
                return;
            }
        }

        // Consultas para vistas y modales
        Vehiculo informeVehiculo = null;
        String patInforme = params.getOrDefault("patente", "").trim();
        if ("informe".equals(vista) && !patInforme.isEmpty()) {
            familia.CrearInforme(patInforme);
            informeVehiculo = familia.BuscarPorPatente(patInforme);
        }

        Vehiculo detalleVehiculo = null;
        String patDetalle = params.getOrDefault("detalle", "").trim();
        if (!patDetalle.isEmpty()) {
            detalleVehiculo = familia.BuscarPorPatente(patDetalle);
        }

        String detPat = params.getOrDefault("det_pat", "").trim();
        int idxRepDetalle = parsearEntero(params.get("det_rep"), -1);
        Vehiculo vehiculoRepDetalle = !detPat.isEmpty() ? familia.BuscarPorPatente(detPat) : null;
        Reparacion detalleReparacion = null;
        if (vehiculoRepDetalle != null && idxRepDetalle >= 0 && idxRepDetalle < vehiculoRepDetalle.reparaciones.size()) {
            detalleReparacion = vehiculoRepDetalle.reparaciones.get(idxRepDetalle);
        }

        String textoBusqueda = params.getOrDefault("buscar", "").trim();
        Vehiculo buscadoVehiculo = !textoBusqueda.isEmpty() ? familia.BuscarPorPatente(textoBusqueda) : null;

        String msg = params.getOrDefault("msg", "");
        String err = params.getOrDefault("err", "");

        String html = construirPaginaHTML(
            vista, msg, err, buscadoVehiculo, textoBusqueda,
            informeVehiculo, patInforme, detalleVehiculo,
            vehiculoRepDetalle, detalleReparacion, idxRepDetalle, params
        );

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String obtenerEstilosCSS() {
        return "<style>"
            + "* { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }"
            + "body { background: #E6EEF2; color: #1E293B; display: flex; min-height: 100vh; }"
            + ".sidebar { width: 265px; background: #222B30; color: #FFFFFF; display: flex; flex-direction: column; justify-content: space-between; padding: 24px 18px; flex-shrink: 0; }"
            + ".logo-wrap { display: flex; flex-direction: column; align-items: center; margin-bottom: 22px; text-decoration: none; }"
            + ".logo-box { background: #FFFFFF; border-radius: 18px; padding: 10px 14px; box-shadow: 0 6px 16px rgba(0,0,0,0.28); display: flex; align-items: center; justify-content: center; }"
            + ".logo-box img { width: 150px; height: auto; display: block; border-radius: 10px; }"
            + ".nav-list { display: flex; flex-direction: column; gap: 8px; }"
            + ".nav-btn { display: block; text-align: center; padding: 13px 14px; border-radius: 10px; color: #E2E8F0; text-decoration: none; font-size: 15px; font-weight: 500; }"
            + ".nav-btn:hover { background: rgba(255,255,255,0.08); color: #FFFFFF; }"
            + ".nav-btn.active { background: #48916D; color: #FFFFFF; font-weight: 600; box-shadow: 0 4px 12px rgba(72,145,109,0.35); }"
            + ".logout-btn { display: flex; align-items: center; justify-content: center; gap: 10px; color: #E2E8F0; text-decoration: none; font-size: 15px; padding: 12px; border-radius: 10px; margin-top: 16px; }"
            + ".logout-btn:hover { background: rgba(255,255,255,0.08); color: #FFFFFF; }"
            + ".main { flex: 1; padding: 24px 36px; max-width: 1280px; }"
            + ".topbar { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 26px; flex-wrap: wrap; }"
            + ".search-form { display: flex; align-items: center; background: #FFFFFF; border: 2px solid #355872; border-radius: 12px; padding: 6px 14px; flex: 1; max-width: 560px; }"
            + ".search-form input { border: none; outline: none; width: 100%; font-size: 15px; color: #1E293B; padding: 4px 8px; }"
            + ".search-form button { background: #355872; color: white; border: none; padding: 7px 14px; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 13px; }"
            + ".admin-pill { display: flex; align-items: center; gap: 10px; background: #355872; color: #FFFFFF; padding: 8px 18px 8px 10px; border-radius: 999px; font-size: 14px; font-weight: 500; }"
            + ".admin-avatar { width: 30px; height: 30px; border-radius: 50%; background: #CBD5E1; color: #1E293B; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px; }"
            + "h1.page-title { font-size: 28px; font-weight: 700; color: #1E293B; margin-bottom: 18px; }"
            + "h2.section-title { font-size: 22px; font-weight: 700; color: #1E293B; margin: 26px 0 14px; }"
            + ".admin-card { position: relative; background: #FFFFFF; border: 3px solid #48916D; border-radius: 18px; padding: 22px 26px; display: flex; align-items: center; gap: 24px; box-shadow: 0 6px 18px rgba(15,23,42,0.06); overflow: hidden; }"
            + ".car-thumb { width: 150px; height: 120px; background: #D8E1E6; border-radius: 14px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }"
            + ".car-thumb-sm { width: 115px; height: 105px; background: #D8E1E6; border-radius: 14px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }"
            + ".admin-info { flex: 1; z-index: 2; }"
            + ".role-badge { display: inline-block; background: #222B30; color: #FFFFFF; font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 999px; margin-bottom: 8px; }"
            + ".role-badge-admin { display: inline-block; background: #48916D; color: #FFFFFF; font-size: 12px; font-weight: 700; padding: 4px 12px; border-radius: 999px; margin-bottom: 8px; }"
            + ".badge-amber { display: inline-block; background: #D97706; color: #FFFFFF; font-size: 12px; font-weight: 700; padding: 4px 12px; border-radius: 999px; margin-bottom: 8px; }"
            + ".person-name { font-size: 23px; font-weight: 700; color: #1E293B; margin-bottom: 4px; }"
            + ".vehicle-sub { font-size: 15px; color: #475569; margin-bottom: 14px; }"
            + ".btn-slate { display: block; width: 100%; max-width: 340px; text-align: center; background: #355872; color: #FFFFFF; text-decoration: none; border: none; padding: 10px 18px; border-radius: 8px; font-size: 15px; font-weight: 500; cursor: pointer; }"
            + ".btn-slate:hover { background: #29465B; }"
            + ".btn-green { background: #48916D; color: #FFFFFF; border: none; padding: 11px 20px; border-radius: 8px; font-size: 15px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-block; text-align: center; }"
            + ".btn-green:hover { background: #3B7A5B; }"
            + ".btn-danger { background: #DC2626; color: #FFFFFF; border: none; padding: 8px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-block; }"
            + ".modal-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.58); display: flex; align-items: center; justify-content: center; padding: 20px; z-index: 999; }"
            + ".modal-card { position: relative; background: #FFFFFF; border: 3px solid #48916D; border-radius: 20px; padding: 28px; width: 100%; max-width: 560px; box-shadow: 0 20px 40px rgba(0,0,0,0.28); overflow: hidden; }"
            + ".detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 14px; padding: 18px; margin: 16px 0 20px; }"
            + ".detail-item span { display: block; font-size: 12px; color: #64748B; text-transform: uppercase; }"
            + ".detail-item strong { font-size: 16px; color: #1E293B; }"
            + ".tab-selector { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }"
            + ".tab-btn { flex: 1; min-width: 220px; max-width: 300px; text-align: center; padding: 15px 22px; border-radius: 12px; font-size: 16px; font-weight: 700; text-decoration: none; border: 2px solid #355872; background: #FFFFFF; color: #355872; }"
            + ".tab-btn.active-add { background: #48916D; border-color: #48916D; color: #FFFFFF; }"
            + ".tab-btn.active-del { background: #355872; border-color: #355872; color: #FFFFFF; }"
            + ".members-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); gap: 22px; }"
            + ".member-card { position: relative; background: #FFFFFF; border-radius: 18px; padding: 20px 22px; display: flex; align-items: center; gap: 18px; box-shadow: 0 4px 14px rgba(15,23,42,0.05); border: 1px solid #D5E0E6; overflow: hidden; }"
            + ".member-info { flex: 1; z-index: 2; }"
            + ".member-info .person-name { font-size: 19px; margin-bottom: 4px; }"
            + ".member-info .vehicle-sub { font-size: 14px; margin-bottom: 14px; }"
            + ".member-info .btn-slate { max-width: 100%; padding: 9px 14px; font-size: 14px; }"
            + ".pixels-tr { position: absolute; top: 0; right: 0; pointer-events: none; }"
            + ".pixels-br { position: absolute; bottom: 0; right: 0; pointer-events: none; }"
            + ".panel-card { background: #FFFFFF; border-radius: 18px; padding: 26px; box-shadow: 0 4px 14px rgba(15,23,42,0.05); border: 1px solid #D5E0E6; position: relative; overflow: hidden; }"
            + ".two-col { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 24px; align-items: start; }"
            + ".form-group { margin-bottom: 15px; }"
            + ".form-group label { display: block; font-size: 14px; font-weight: 600; color: #334155; margin-bottom: 6px; }"
            + ".form-group input, .form-group select { width: 100%; padding: 10px 12px; border: 1.5px solid #CBD5E1; border-radius: 10px; font-size: 14px; background: #F8FAFC; }"
            + ".vehicle-pill { background: #E6EEF2; border: 1.5px solid #CBD5E1; border-radius: 10px; padding: 11px 14px; font-weight: 700; color: #1E293B; font-size: 14px; }"
            + ".cita-item { background: #F8FAFC; border: 1.5px solid #E2E8F0; border-radius: 14px; padding: 16px; margin-bottom: 12px; }"
            + ".alert-ok { background: #DCFCE7; border: 1.5px solid #48916D; color: #14532D; padding: 12px 18px; border-radius: 12px; margin-bottom: 20px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; }"
            + ".alert-err { background: #FEE2E2; border: 1.5px solid #EF4444; color: #7F1D1D; padding: 12px 18px; border-radius: 12px; margin-bottom: 20px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; }"
            + "</style>";
    }

    private static String construirPaginaHTML(
            String vista,
            String msg,
            String err,
            Vehiculo buscado,
            String textoBusqueda,
            Vehiculo informe,
            String patInforme,
            Vehiculo detalle,
            Vehiculo vehiculoRepDetalle,
            Reparacion detalleRep,
            int idxRepDetalle,
            Map<String, String> params) {

        Vehiculo admin = familia.administrador;
        StringBuilder sb = new StringBuilder();

        add(sb, "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>");
        add(sb, "<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        add(sb, "<title>BitCar - Panel Familiar</title>");
        add(sb, obtenerEstilosCSS());
        add(sb, "</head><body>");

        // SIDEBAR
        add(sb, "<aside class='sidebar'><div>");
        add(sb, "<a href='/?vista=flota' class='logo-wrap'><div class='logo-box'><img src='/logo' alt='Logo BitCar'></div></a>");
        add(sb, "<nav class='nav-list'>");
        add(sb, "<a class='nav-btn ", "flota".equals(vista) ? "active" : "", "' href='/?vista=flota'>Integrantes y Vehículos</a>");
        add(sb, "<a class='nav-btn ", "revision".equals(vista) ? "active" : "", "' href='/?vista=revision'>Registrar Revisión</a>");
        add(sb, "<a class='nav-btn ", "informe".equals(vista) ? "active" : "", "' href='/?vista=informe'>Crear Informe</a>");
        add(sb, "<a class='nav-btn ", "modificar".equals(vista) ? "active" : "", "' href='/?vista=modificar'>Modificar Grupo Familiar</a>");
        add(sb, "<a class='nav-btn ", "citas".equals(vista) ? "active" : "", "' href='/?vista=citas'>Agendar Cita</a>");
        add(sb, "<a class='nav-btn ", "reparaciones".equals(vista) ? "active" : "", "' href='/?vista=reparaciones'>Reparaciones Pendientes</a>");
        add(sb, "</nav></div>");
        add(sb, "<a class='logout-btn' href='/?vista=sesion'>Cerrar Sesión &nbsp;&#10140;</a>");
        add(sb, "</aside>");

        // CONTENIDO PRINCIPAL
        add(sb, "<main class='main'>");

        add(sb, "<div class='topbar'>");
        add(sb, "<form class='search-form' method='GET' action='/'>");
        add(sb, "<input type='hidden' name='vista' value='", esc(vista), "'>");
        add(sb, "<span style='color:#64748B;font-size:16px;'>&#128269;</span>");
        add(sb, "<input type='text' name='buscar' placeholder='Buscar por patente...' value='", esc(textoBusqueda), "'>");
        add(sb, "<button type='submit'>Buscar</button></form>");
        add(sb, "<div class='admin-pill'><div class='admin-avatar'>FP</div><span>", esc(admin.nombre_dueno), " &middot; Administrador</span></div>");
        add(sb, "</div>");

        String subActual = params.getOrDefault("subvista", "");
        String enlaceCerrarAlerta = "/?vista=" + esc(vista) + (subActual.isEmpty() ? "" : "&subvista=" + esc(subActual));

        if (!msg.isEmpty()) {
            add(sb, "<div class='alert-ok'><span>&#9989; ", esc(msg), "</span>");
            add(sb, "<a href='", enlaceCerrarAlerta, "' style='color:#14532D;text-decoration:none;'>&#10005;</a></div>");
        }
        if (!err.isEmpty()) {
            add(sb, "<div class='alert-err'><span>&#10060; ", esc(err), "</span>");
            add(sb, "<a href='", enlaceCerrarAlerta, "' style='color:#7F1D1D;text-decoration:none;'>&#10005;</a></div>");
        }

        if (!textoBusqueda.isEmpty()) {
            if (buscado != null) {
                add(sb, "<div class='panel-card' style='margin-bottom:22px;border:2px solid #355872;'>");
                add(sb, "<div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;'>");
                add(sb, "<span class='role-badge' style='background:#355872;'>Vehículo Encontrado (", esc(buscado.patente), ")</span>");
                add(sb, "<a href='/?vista=", esc(vista), "' style='color:#355872;font-weight:600;text-decoration:none;'>Cerrar &#10005;</a></div>");
                add(sb, "<p style='font-size:15px;'><strong>", esc(buscado.nombre_dueno), "</strong> &bull; ");
                add(sb, esc(buscado.marca), " ", esc(buscado.modelo), " &bull; Patente: <strong>", esc(buscado.patente), "</strong>");
                add(sb, " &bull; Kilometraje: ", formatearKm(buscado.kilometraje), " km &bull; Última revisión: ", esc(buscado.ultima_fecha), "</p>");
                add(sb, "<div style='margin-top:12px;display:flex;gap:10px;flex-wrap:wrap;'>");
                add(sb, "<a class='btn-green' href='/?vista=revision&patente=", esc(buscado.patente), "'>Registrar Revisión</a>");
                add(sb, "<a class='btn-slate' style='max-width:170px;' href='/?vista=informe&patente=", esc(buscado.patente), "'>Ver Informe</a>");
                add(sb, "</div></div>");
            } else {
                add(sb, "<div class='alert-err'><span>Vehículo no encontrado con patente \"", esc(textoBusqueda), "\".</span>");
                add(sb, "<a href='/?vista=", esc(vista), "' style='color:#7F1D1D;text-decoration:none;'>Cerrar</a></div>");
            }
        }

        if ("revision".equals(vista)) {
            String patPre = params.getOrDefault("patente", "");
            add(sb, "<h1 class='page-title'>Registrar Revisión Mecánica</h1>");
            add(sb, "<div class='panel-card' style='max-width:620px;'>");
            add(sb, "<p style='color:#475569;margin-bottom:18px;'>Selecciona el vehículo para actualizar su mantención y kilometraje.</p>");
            add(sb, "<form method='GET' action='/'>");
            add(sb, "<input type='hidden' name='accion' value='mantencion'>");
            add(sb, "<div class='form-group'><label>Vehículo / Integrante</label>");
            add(sb, "<select name='patente' required>", generarOpcionesVehiculos(patPre), "</select></div>");
            add(sb, "<div class='form-group'><label>Detalle de la revisión</label>");
            add(sb, "<input type='text' name='detalle' required placeholder='Ej: Cambio de aceite y filtros'></div>");
            add(sb, "<div class='form-group'><label>Nuevo kilometraje</label>");
            add(sb, "<input type='number' name='km' required placeholder='Ej: 9500'></div>");
            add(sb, "<button type='submit' class='btn-green' style='width:100%;'>Guardar Revisión</button>");
            add(sb, "</form></div>");

        } else if ("informe".equals(vista)) {
            add(sb, "<h1 class='page-title'>Crear Informe de Revisión</h1>");
            add(sb, "<div class='two-col'><div class='panel-card'>");
            add(sb, "<h3 style='margin-bottom:10px;'>Seleccionar Vehículo</h3>");
            add(sb, "<p style='color:#475569;font-size:14px;margin-bottom:16px;'>Selecciona el vehículo del grupo familiar para generar su informe.</p>");
            add(sb, "<form method='GET' action='/'>");
            add(sb, "<input type='hidden' name='vista' value='informe'>");
            add(sb, "<div class='form-group'><label>Vehículo de la familia</label>");
            add(sb, "<select name='patente' required>", generarOpcionesVehiculos(patInforme), "</select></div>");
            add(sb, "<button type='submit' class='btn-slate' style='max-width:100%;'>Generar Vista Previa de Informe</button>");
            add(sb, "</form></div>");

            if (informe != null) {
                add(sb, "<div class='panel-card' style='border:2.5px solid #48916D;'>");
                add(sb, svgPixelesTopRight());
                add(sb, "<span class='role-badge' style='background:#48916D;'>INFORME VEHICULAR BITCAR</span>");
                add(sb, "<h2 style='margin:10px 0 14px;'>Patente: ", esc(informe.patente), "</h2>");
                add(sb, "<div style='background:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;line-height:1.8;font-size:15px;'>");
                add(sb, "<strong>Dueño del vehículo:</strong> ", esc(informe.nombre_dueno), "<br>");
                add(sb, "<strong>Marca y Modelo:</strong> ", esc(informe.marca), " ", esc(informe.modelo), "<br>");
                add(sb, "<strong>Kilometraje registrado:</strong> ", formatearKm(informe.kilometraje), " km<br>");
                add(sb, "<strong>Última revisión:</strong> ", esc(informe.ultima_fecha), "<br>");
                add(sb, "<strong>Citas:</strong> ", String.valueOf(informe.citas.size()), " | <strong>Reparaciones/Repuestos:</strong> ", String.valueOf(informe.reparaciones.size()));
                add(sb, "</div><br><button type='button' onclick='window.print()' class='btn-green'>Imprimir / Guardar PDF</button></div>");
            } else {
                add(sb, "<div class='panel-card' style='display:flex;align-items:center;justify-content:center;color:#64748B;text-align:center;'>");
                add(sb, "<p>Selecciona un vehículo a la izquierda y presiona <strong>Generar Vista Previa de Informe</strong>.</p></div>");
            }
            add(sb, "</div>");

        } else if ("modificar".equals(vista)) {
            String subvista = params.getOrDefault("subvista", "");
            add(sb, "<h1 class='page-title'>Modificar Grupo Familiar</h1>");
            add(sb, "<div class='tab-selector'>");
            add(sb, "<a class='tab-btn ", "agregar".equals(subvista) ? "active-add" : "", "' href='/?vista=modificar&subvista=agregar'>Añadir miembro</a>");
            add(sb, "<a class='tab-btn ", "eliminar".equals(subvista) ? "active-del" : "", "' href='/?vista=modificar&subvista=eliminar'>Eliminar miembro</a>");
            add(sb, "</div>");

            if ("agregar".equals(subvista)) {
                add(sb, "<div class='panel-card' style='max-width:600px;'>");
                add(sb, "<h3 style='margin:0 0 16px;'>Añadir Miembro a la Familia</h3>");
                add(sb, "<form method='GET' action='/'>");
                add(sb, "<input type='hidden' name='accion' value='agregar'>");
                add(sb, "<div class='form-group'><label>Nombre del dueño</label><input type='text' name='dueno' required placeholder='Ej: Camila Rojas'></div>");
                add(sb, "<div class='form-group'><label>Patente</label><input type='text' name='patente' required placeholder='Ej: GHJK89'></div>");
                add(sb, "<div class='form-group'><label>Marca</label><input type='text' name='marca' required placeholder='Ej: Hyundai'></div>");
                add(sb, "<div class='form-group'><label>Modelo</label><input type='text' name='modelo' required placeholder='Ej: Accent'></div>");
                add(sb, "<div class='form-group'><label>Kilometraje actual</label><input type='number' name='km' required placeholder='Ej: 18500'></div>");
                add(sb, "<button type='submit' class='btn-green' style='width:100%;'>Añadir Miembro</button>");
                add(sb, "</form></div>");
            } else if ("eliminar".equals(subvista)) {
                add(sb, "<div class='panel-card' style='max-width:680px;'>");
                add(sb, "<h3 style='margin:0 0 16px;'>Eliminar Miembro del Grupo</h3>");
                for (int i = 0; i < familia.grupo_familia.size(); i++) {
                    Vehiculo m = familia.grupo_familia.get(i);
                    add(sb, "<div style='display:flex;justify-content:space-between;align-items:center;padding:12px;border:1px solid #E2E8F0;border-radius:12px;margin-bottom:10px;background:#F8FAFC;'>");
                    add(sb, "<div><span class='role-badge'>Integrante ", String.valueOf(i + 2), "</span><br>");
                    add(sb, "<strong>", esc(m.nombre_dueno), "</strong> &bull; ", esc(m.marca), " ", esc(m.modelo), " (<strong>", esc(m.patente), "</strong>)</div>");
                    add(sb, "<a class='btn-danger' href='/?accion=eliminar&patente=", esc(m.patente), "'>Eliminar miembro</a>");
                    add(sb, "</div>");
                }
                add(sb, "</div>");
            } else {
                add(sb, "<div class='panel-card' style='max-width:616px;color:#475569;'><p>Presiona <strong>Añadir miembro</strong> o <strong>Eliminar miembro</strong> para continuar.</p></div>");
            }

        } else if ("citas".equals(vista)) {
            add(sb, "<h1 class='page-title'>Agendar Cita</h1>");
            add(sb, "<div class='two-col'>");

            // Formulario exclusivo del Administrador
            add(sb, "<div class='panel-card' style='border:2.5px solid #48916D;'>");
            add(sb, svgPixelesTopRight());
            add(sb, "<h2 style='font-size:21px;margin-bottom:16px;'>Agendar Nueva Cita</h2>");
            add(sb, "<form method='GET' action='/'>");
            add(sb, "<input type='hidden' name='accion' value='agendar_cita'>");
            add(sb, "<div class='form-group'><label>Mi Vehículo</label>");
            add(sb, "<div class='vehicle-pill'>", esc(admin.marca), " ", esc(admin.modelo), " (", esc(admin.patente), ") &middot; ", esc(admin.nombre_dueno), "</div></div>");
            add(sb, "<div class='form-group'><label>Tipo de servicio</label><select name='servicio' required>");
            add(sb, "<option value='Revisión general'>Revisión general</option>");
            add(sb, "<option value='Cambio de aceite y filtros'>Cambio de aceite y filtros</option>");
            add(sb, "<option value='Alineación y balanceo'>Alineación y balanceo</option>");
            add(sb, "<option value='Revisión de frenos'>Revisión de frenos</option>");
            add(sb, "</select></div>");
            add(sb, "<div class='form-group'><label>Fecha</label><input type='date' name='fecha' required value='2026-12-18'></div>");
            add(sb, "<div class='form-group'><label>Hora</label><input type='time' name='hora' required value='18:00'></div>");
            add(sb, "<button type='submit' class='btn-green' style='width:100%;'>Confirmar Cita</button>");
            add(sb, "</form></div>");

            // Mis citas (gestionables) + Citas del grupo familiar (solo lectura)
            add(sb, "<div class='panel-card'>");
            add(sb, svgPixelesTopRight());
            add(sb, "<h2 style='font-size:21px;margin-bottom:14px;'>Mis Citas Agendadas</h2>");

            for (int i = 0; i < admin.citas.size(); i++) {
                Cita c = admin.citas.get(i);
                boolean esPendiente = "Pendiente".equalsIgnoreCase(c.estado);
                String claseBadge = esPendiente ? "badge-amber" : "role-badge-admin";
                add(sb, "<div class='cita-item' style='display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;'>");
                add(sb, "<div>");
                add(sb, "<span class='", claseBadge, "'>", esc(c.estado), "</span>");
                add(sb, "<div style='font-size:17px;font-weight:700;color:#1E293B;margin-bottom:4px;'>");
                add(sb, esc(c.tipo_servicio), " &middot; ", esc(admin.marca), " ", esc(admin.modelo), " (", esc(admin.patente), ")</div>");
                add(sb, "<div style='font-size:14px;color:#475569;'><strong>", esc(c.fecha), "</strong> &middot; <strong>", esc(c.hora), "</strong></div>");
                add(sb, "</div>");
                if (esPendiente) {
                    add(sb, "<a class='btn-green' style='padding:8px 12px;font-size:13px;' href='/?accion=completar_cita&idx=", String.valueOf(i), "'>Completada</a>");
                }
                add(sb, "</div>");
            }

            add(sb, "<h2 style='font-size:19px;margin:22px 0 12px;color:#334155;'>Citas del Grupo Familiar</h2>");
            for (Vehiculo v : familia.grupo_familia) {
                for (Cita c : v.citas) {
                    boolean esPendiente = "Pendiente".equalsIgnoreCase(c.estado);
                    String claseBadge = esPendiente ? "role-badge" : "role-badge-admin";
                    add(sb, "<div class='cita-item'>");
                    add(sb, "<span class='", claseBadge, "'>", esc(c.estado), "</span>");
                    add(sb, "<div style='font-size:16px;font-weight:700;color:#1E293B;margin-bottom:4px;'>");
                    add(sb, esc(v.marca), " ", esc(v.modelo), " (", esc(v.patente), ") &middot; ", esc(c.tipo_servicio), "</div>");
                    add(sb, "<div style='font-size:14px;color:#475569;'>", esc(v.nombre_dueno), " &middot; <strong>", esc(c.fecha), "</strong> &middot; <strong>", esc(c.hora), "</strong></div>");
                    add(sb, "</div>");
                }
            }
            add(sb, "</div></div>");

        } else if ("reparaciones".equals(vista)) {
            add(sb, "<h1 class='page-title'>Reparaciones y Repuestos Pendientes</h1>");
            add(sb, "<h2 class='section-title' style='margin-top:8px;'>Mis Reparaciones y Repuestos Pendientes</h2>");
            add(sb, "<div class='members-grid'>");

            boolean hayPendientesAdmin = false;
            for (int i = 0; i < admin.reparaciones.size(); i++) {
                Reparacion r = admin.reparaciones.get(i);
                if ("Pendiente".equalsIgnoreCase(r.estado)) {
                    hayPendientesAdmin = true;
                    add(sb, "<div class='member-card' style='border:2px solid #48916D;'>");
                    add(sb, svgPixelesTopRight());
                    add(sb, "<div class='car-thumb-sm'>", svgCamionetaRaptor(), "</div>");
                    add(sb, "<div class='member-info'>");
                    add(sb, "<span class='badge-amber'>", esc(r.categoria), " &middot; ", esc(r.estado), "</span>");
                    add(sb, "<div class='person-name'>", esc(r.descripcion), "</div>");
                    add(sb, "<div class='vehicle-sub'>", esc(admin.marca), " ", esc(admin.modelo), " (", esc(admin.patente), ") &middot; ", esc(admin.nombre_dueno), "</div>");
                    add(sb, "<a class='btn-slate' href='/?vista=reparaciones&det_pat=", esc(admin.patente), "&det_rep=", String.valueOf(i), "'>Ver Detalle</a>");
                    add(sb, "</div></div>");
                }
            }
            if (!hayPendientesAdmin) {
                add(sb, "<div class='panel-card' style='color:#64748B;'>No tienes reparaciones ni repuestos pendientes.</div>");
            }
            add(sb, "</div>");

            add(sb, "<h2 class='section-title'>Reparaciones y Repuestos de la Familia</h2>");
            add(sb, "<div class='members-grid'>");

            for (Vehiculo v : familia.grupo_familia) {
                for (int i = 0; i < v.reparaciones.size(); i++) {
                    Reparacion r = v.reparaciones.get(i);
                    if ("Pendiente".equalsIgnoreCase(r.estado)) {
                        add(sb, "<div class='member-card'>");
                        add(sb, svgPixelesTopRight());
                        add(sb, "<div class='car-thumb-sm'>", svgSedanFamiliar(), "</div>");
                        add(sb, "<div class='member-info'>");
                        add(sb, "<span class='role-badge'>", esc(r.categoria), " &middot; ", esc(r.estado), "</span>");
                        add(sb, "<div class='person-name'>", esc(r.descripcion), "</div>");
                        add(sb, "<div class='vehicle-sub'>", esc(v.marca), " ", esc(v.modelo), " (", esc(v.patente), ") &middot; ", esc(v.nombre_dueno), "</div>");
                        add(sb, "<a class='btn-slate' href='/?vista=reparaciones&det_pat=", esc(v.patente), "&det_rep=", String.valueOf(i), "'>Ver Detalle</a>");
                        add(sb, "</div></div>");
                    }
                }
            }
            add(sb, "</div>");

            // Formulario exclusivo del Administrador
            add(sb, "<h2 class='section-title'>Agregar Reparación o Repuesto Pendiente</h2>");
            add(sb, "<div class='panel-card' style='max-width:680px;'>");
            add(sb, "<form method='GET' action='/'>");
            add(sb, "<input type='hidden' name='accion' value='agregar_reparacion'>");
            add(sb, "<div class='form-group'><label>Mi Vehículo</label>");
            add(sb, "<div class='vehicle-pill'>", esc(admin.marca), " ", esc(admin.modelo), " (", esc(admin.patente), ") &middot; ", esc(admin.nombre_dueno), "</div></div>");
            add(sb, "<div class='form-group'><label>Categoría</label><select name='categoria' required><option value='Reparacion'>Reparacion</option><option value='Revision'>Revision</option><option value='Repuesto'>Repuesto</option></select></div>");
            add(sb, "<div class='form-group'><label>Descripción</label><input type='text' name='descripcion' required placeholder='Ej: Cambio de amortiguadores'></div>");
            add(sb, "<button type='submit' class='btn-green' style='width:100%;'>Guardar Pendiente</button>");
            add(sb, "</form></div>");

        } else if ("sesion".equals(vista)) {
            add(sb, "<div class='panel-card' style='max-width:480px;margin:40px auto;text-align:center;'>");
            add(sb, "<h2>Sesión Finalizada</h2>");
            add(sb, "<p style='color:#475569;margin:12px 0 20px;'>Has cerrado la sesión de <strong>", esc(admin.nombre_dueno), "</strong>.</p>");
            add(sb, "<a class='btn-green' href='/?vista=flota'>Volver a ingresar</a></div>");

        } else {
            add(sb, "<h1 class='page-title'>Integrantes y Vehículos</h1>");
            add(sb, "<div class='admin-card'>");
            add(sb, svgPixelesTopRight());
            add(sb, svgPixelesBottomRight());
            add(sb, "<div class='car-thumb'>", svgCamionetaRaptor(), "</div>");
            add(sb, "<div class='admin-info'>");
            add(sb, "<span class='role-badge-admin'>Integrante 1 &middot; Administrador</span>");
            add(sb, "<div class='person-name'>Administrador: ", esc(admin.nombre_dueno), "</div>");
            add(sb, "<div class='vehicle-sub'>Modelo: <strong>", esc(admin.marca), " ", esc(admin.modelo), "</strong></div>");
            add(sb, "<a class='btn-slate' href='/?vista=flota&detalle=", esc(admin.patente), "'>Ver Detalle</a>");
            add(sb, "</div></div>");

            add(sb, "<h2 class='section-title'>Integrantes de la Familia</h2>");
            add(sb, "<div class='members-grid'>");

            for (int i = 0; i < familia.grupo_familia.size(); i++) {
                Vehiculo v = familia.grupo_familia.get(i);
                add(sb, "<div class='member-card'>");
                add(sb, svgPixelesTopRight());
                add(sb, "<div class='car-thumb-sm'>", svgSedanFamiliar(), "</div>");
                add(sb, "<div class='member-info'>");
                add(sb, "<span class='role-badge'>Integrante ", String.valueOf(i + 2), "</span>");
                add(sb, "<div class='person-name'>", esc(v.nombre_dueno), "</div>");
                add(sb, "<div class='vehicle-sub'>Modelo: <strong>", esc(v.marca), " ", esc(v.modelo), "</strong></div>");
                add(sb, "<a class='btn-slate' href='/?vista=flota&detalle=", esc(v.patente), "'>Ver Detalle</a>");
                add(sb, "</div></div>");
            }
            add(sb, "</div>");
        }

        // Modal Detalle de Vehículo
        if (detalle != null) {
            add(sb, "<div class='modal-backdrop'><div class='modal-card'>");
            add(sb, svgPixelesTopRight());
            add(sb, "<span class='role-badge-admin'>", esc(obtenerEtiquetaIntegrante(detalle)), "</span>");
            add(sb, "<h2 style='font-size:24px;color:#1E293B;margin-top:6px;'>", esc(detalle.nombre_dueno), "</h2>");
            add(sb, "<div class='detail-grid'>");
            add(sb, "<div class='detail-item'><span>Marca y Modelo</span><strong>", esc(detalle.marca), " ", esc(detalle.modelo), "</strong></div>");
            add(sb, "<div class='detail-item'><span>Patente</span><strong>", esc(detalle.patente), "</strong></div>");
            add(sb, "<div class='detail-item'><span>Kilometraje Actual</span><strong>", formatearKm(detalle.kilometraje), " km</strong></div>");
            add(sb, "<div class='detail-item'><span>Última Revisión</span><strong>", esc(detalle.ultima_fecha), "</strong></div>");
            add(sb, "</div>");
            add(sb, "<div style='display:flex;gap:10px;flex-wrap:wrap;'>");
            add(sb, "<a class='btn-green' href='/?vista=revision&patente=", esc(detalle.patente), "'>Registrar Revisión</a>");
            add(sb, "<a class='btn-slate' style='max-width:160px;' href='/?vista=informe&patente=", esc(detalle.patente), "'>Crear Informe</a>");
            add(sb, "<a class='btn-danger' style='padding:11px 16px;font-size:14px;' href='/?vista=flota'>Cerrar</a>");
            add(sb, "</div></div></div>");
        }

        // Modal Detalle de Reparación / Repuesto (botón Completada solo si pertenece al Administrador)
        if (detalleRep != null && vehiculoRepDetalle != null) {
            boolean esPropio = vehiculoRepDetalle.patente.equalsIgnoreCase(admin.patente);
            add(sb, "<div class='modal-backdrop'><div class='modal-card'>");
            add(sb, svgPixelesTopRight());
            add(sb, "<span class='badge-amber'>", esc(detalleRep.categoria), " &middot; ", esc(detalleRep.estado), "</span>");
            add(sb, "<h2 style='font-size:22px;color:#1E293B;margin-top:6px;'>", esc(detalleRep.descripcion), "</h2>");
            add(sb, "<div class='detail-grid'>");
            add(sb, "<div class='detail-item'><span>Vehículo</span><strong>", esc(vehiculoRepDetalle.marca), " ", esc(vehiculoRepDetalle.modelo), "</strong></div>");
            add(sb, "<div class='detail-item'><span>Patente</span><strong>", esc(vehiculoRepDetalle.patente), "</strong></div>");
            add(sb, "<div class='detail-item'><span>Responsable</span><strong>", esc(vehiculoRepDetalle.nombre_dueno), "</strong></div>");
            add(sb, "<div class='detail-item'><span>Estado</span><strong>", esc(detalleRep.estado), "</strong></div>");
            add(sb, "</div>");
            add(sb, "<div style='display:flex;gap:10px;flex-wrap:wrap;'>");
            if (esPropio) {
                add(sb, "<a class='btn-green' href='/?accion=completar_rep&idx=", String.valueOf(idxRepDetalle), "'>Marcar como Completada</a>");
            }
            add(sb, "<a class='btn-danger' style='padding:11px 16px;font-size:14px;' href='/?vista=reparaciones'>Cerrar</a>");
            add(sb, "</div></div></div>");
        }

        add(sb, "</main></body></html>");
        return sb.toString();
    }

    private static void add(StringBuilder sb, String... partes) {
        for (String p : partes) {
            sb.append(p);
        }
    }

    private static String obtenerEtiquetaIntegrante(Vehiculo v) {
        if (v.patente.equalsIgnoreCase(familia.administrador.patente)) {
            return "Integrante 1 · Administrador";
        }
        for (int i = 0; i < familia.grupo_familia.size(); i++) {
            if (familia.grupo_familia.get(i).patente.equalsIgnoreCase(v.patente)) {
                return "Integrante " + (i + 2);
            }
        }
        return "Integrante";
    }

    private static String generarOpcionesVehiculos(String seleccionada) {
        StringBuilder opts = new StringBuilder();
        Vehiculo admin = familia.administrador;
        String selAdmin = admin.patente.equalsIgnoreCase(seleccionada) ? "selected" : "";
        add(opts, "<option value='", esc(admin.patente), "' ", selAdmin, ">");
        add(opts, "Integrante 1: ", esc(admin.nombre_dueno), " - ", esc(admin.marca), " ", esc(admin.modelo), " (", esc(admin.patente), ")</option>");

        for (int i = 0; i < familia.grupo_familia.size(); i++) {
            Vehiculo v = familia.grupo_familia.get(i);
            String sel = v.patente.equalsIgnoreCase(seleccionada) ? "selected" : "";
            add(opts, "<option value='", esc(v.patente), "' ", sel, ">");
            add(opts, "Integrante ", String.valueOf(i + 2), ": ", esc(v.nombre_dueno), " - ", esc(v.marca), " ", esc(v.modelo), " (", esc(v.patente), ")</option>");
        }
        return opts.toString();
    }

    private static String formatearFecha(String iso) {
        if (iso != null && iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = iso.split("-");
            return p[2] + "/" + p[1] + "/" + p[0];
        }
        return iso;
    }

    private static String svgLogoRespaldo() {
        return "<svg width='160' height='110' viewBox='0 0 220 150' xmlns='http://www.w3.org/2000/svg'>"
             + "<text x='110' y='88' text-anchor='middle' font-family='Segoe UI, sans-serif' font-size='44' font-weight='900' fill='#222B30'>Bit<tspan fill='#48916D'>Car</tspan></text>"
             + "</svg>";
    }

    private static String svgCamionetaRaptor() {
        return "<svg width='135' height='85' viewBox='0 0 180 110' xmlns='http://www.w3.org/2000/svg'>"
             + "<ellipse cx='90' cy='96' rx='76' ry='8' fill='#B8C4CC'/>"
             + "<path d='M16 68 L26 42 L56 36 L74 22 L126 22 L138 42 L166 46 L168 74 L14 74 Z' fill='#525E66'/>"
             + "<path d='M78 27 L122 27 L131 42 L78 42 Z' fill='#CBD5E1'/>"
             + "<rect x='14' y='62' width='154' height='12' rx='3' fill='#2E373D'/>"
             + "<circle cx='46' cy='78' r='17' fill='#1E293B' stroke='#94A3B8' stroke-width='4'/>"
             + "<circle cx='46' cy='78' r='7' fill='#CBD5E1'/>"
             + "<circle cx='138' cy='78' r='17' fill='#1E293B' stroke='#94A3B8' stroke-width='4'/>"
             + "<circle cx='138' cy='78' r='7' fill='#CBD5E1'/>"
             + "</svg>";
    }

    private static String svgSedanFamiliar() {
        return "<svg width='105' height='75' viewBox='0 0 160 100' xmlns='http://www.w3.org/2000/svg'>"
             + "<ellipse cx='80' cy='86' rx='66' ry='7' fill='#B8C4CC'/>"
             + "<path d='M16 62 L32 42 L56 26 L108 26 L130 44 L146 52 L146 68 L14 68 Z' fill='#64748B'/>"
             + "<path d='M60 31 L104 31 L120 44 L48 44 Z' fill='#E2E8F0'/>"
             + "<circle cx='44' cy='70' r='14' fill='#1E293B' stroke='#94A3B8' stroke-width='3.5'/>"
             + "<circle cx='44' cy='70' r='6' fill='#CBD5E1'/>"
             + "<circle cx='118' cy='70' r='14' fill='#1E293B' stroke='#94A3B8' stroke-width='3.5'/>"
             + "<circle cx='118' cy='70' r='6' fill='#CBD5E1'/>"
             + "</svg>";
    }

    private static String svgPixelesTopRight() {
        return "<svg class='pixels-tr' width='70' height='55' viewBox='0 0 70 55' xmlns='http://www.w3.org/2000/svg'>"
             + "<rect x='46' y='4' width='10' height='10' fill='#48916D'/>"
             + "<rect x='58' y='4' width='10' height='10' fill='#7AC19E'/>"
             + "<rect x='34' y='16' width='10' height='10' fill='#7AC19E'/>"
             + "<rect x='58' y='16' width='10' height='10' fill='#48916D'/>"
             + "<rect x='46' y='28' width='10' height='10' fill='#48916D'/>"
             + "<rect x='58' y='40' width='10' height='10' fill='#A7D7C0'/>"
             + "</svg>";
    }

    private static String svgPixelesBottomRight() {
        return "<svg class='pixels-br' width='70' height='55' viewBox='0 0 70 55' xmlns='http://www.w3.org/2000/svg'>"
             + "<rect x='58' y='8' width='10' height='10' fill='#A7D7C0'/>"
             + "<rect x='46' y='20' width='10' height='10' fill='#48916D'/>"
             + "<rect x='34' y='32' width='10' height='10' fill='#7AC19E'/>"
             + "<rect x='58' y='32' width='10' height='10' fill='#48916D'/>"
             + "<rect x='46' y='43' width='10' height='10' fill='#7AC19E'/>"
             + "<rect x='58' y='43' width='10' height='10' fill='#48916D'/>"
             + "</svg>";
    }

    private static String formatearKm(int km) {
        return String.format(Locale.GERMAN, "%,d", km);
    }

    private static Map<String, String> leerParametros(String query) {
        Map<String, String> mapa = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return mapa;
        }
        for (String par : query.split("&")) {
            String[] kv = par.split("=", 2);
            try {
                String k = URLDecoder.decode(kv[0], "UTF-8");
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
                mapa.put(k, v);
            } catch (Exception e) {
                System.err.println("Error leyendo parámetro: " + e.getMessage());
            }
        }
        return mapa;
    }

    private static void redirigir(HttpExchange exchange, String destino) throws IOException {
        exchange.getResponseHeaders().set("Location", destino);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static String codificar(String texto) {
        try {
            return URLEncoder.encode(texto, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static int parsearEntero(String s, int defecto) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defecto;
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}