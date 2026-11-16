/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.bitcar;
import java.util.ArrayList;
/**
 *
 * @author Fernando
 */
public class Familia {
    public Vehiculo administrador;
    ArrayList <Vehiculo> grupo_familia;
    
    public Familia(){
        grupo_familia = new ArrayList<>();
        
        //datos semilla precargados en el constructor
        administrador=new Vehiculo("Fernando Perez", "RXPL52", "Ford", "Raptor", 6767);
        Vehiculo v1=new Vehiculo("Nicolas Cerna", "XDBL67", "Nissan", "Versa", 8790);
        Vehiculo v2=new Vehiculo("Sebastian Estrada", "JCFP67", "Toyota", "Corolla", 39692);
        grupo_familia.add(v1);
        grupo_familia.add(v2);
        
        //datos semilla de citas (nacen con estado = "Pendiente")
        administrador.AgendarCita("Revisión general", "18 de diciembre", "18:00");
        v1.AgendarCita("Alineación y balanceo", "18 de diciembre", "18:00");
        v2.AgendarCita("Cambio de aceite", "18 de diciembre", "18:00");
        
        //datos semilla de reparaciones/repuestos (nacen con estado = "Pendiente")
        administrador.AgregarReparacion("Reparacion", "Falla frenos");
        v1.AgregarReparacion("Revision", "Revision sistema electrico");
        v2.AgregarReparacion("Repuesto", "Rueda");
    }
    
    public void AñadirMiembro(Vehiculo NuevoVec){
        grupo_familia.add(NuevoVec);
        System.out.println("Nuevo miembro agregado");
    }
    
    public void QuitarMiembro(String patente){
        for (int i=0;i<grupo_familia.size();i++){
            if (grupo_familia.get(i).patente.equalsIgnoreCase(patente)){
                System.out.println("Se elimino el vehiculo de: "+grupo_familia.get(i).nombre_dueno);
                grupo_familia.remove(i);
            }
        }
    }
    
    public void verPendientesFamilia(){
        for (int i=0;i<grupo_familia.size();i++){
            grupo_familia.get(i).mostrarCitasYReparaciones();
        }
    }
    
    public Vehiculo BuscarPorPatente(String patente){
        Vehiculo v = null;
        
        if (administrador.patente.equalsIgnoreCase(patente)){
            v=administrador;
        }
        
        for (int i=0;i<grupo_familia.size();i++){
            if (grupo_familia.get(i).patente.equalsIgnoreCase(patente)){
                v=grupo_familia.get(i);
            }
        }
        return v;
    }
    
    public void CrearInforme(String patente){
        Vehiculo v=BuscarPorPatente(patente);
        if (v==null) {
            System.out.println("-> [Error] Vehículo no encontrado.");
            return;
        }

        System.out.println("\n--- VISTA PREVIA: INFORME VEHICULAR (" + v.patente + ") ---");
        System.out.println("Dueño: " + v.nombre_dueno + " | Última revisión: " + v.ultima_fecha);
        System.out.println("-> [OK] Datos recopilados (Módulo de exportación de archivo pendiente).");
    }
    
    
    @Override
    public String toString(){
        String resultado;
        resultado= "Administrador:\n"+administrador+"\n\n";
        
        for (int i=0;i<grupo_familia.size();i++){
            resultado+="Integrante "+(i+2)+":\n"+grupo_familia.get(i)+"\n\n";
        }
        return resultado;
    }
    
    
}
