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
public class Vehiculo {
    public String nombre_dueno;
    public String patente;
    public String marca;
    public String modelo;
    public int kilometraje;
    public String ultima_fecha;
    public ArrayList <Cita> citas;
    public ArrayList <Reparacion> reparaciones;
    
    
    public Vehiculo(String n_d,String p, String ma, String mo,int km){
        nombre_dueno=n_d;
        patente=p;
        marca=ma;
        modelo=mo;
        kilometraje=km;
        ultima_fecha="No ha habido ninguna";
        citas=new ArrayList();
        reparaciones=new ArrayList();
    }
    
    public void AgendarCita(String Tipo_servicio,String fecha,String hora){
        Cita nueva=new Cita(Tipo_servicio,fecha,hora);
        citas.add(nueva);
        System.out.println("Nueva cita agendada de:"+Tipo_servicio+"Para: "+fecha+" A las: "+hora);
    }
    
    public void AgregarReparacion(String categoria,String descripcion){
        Reparacion nueva=new Reparacion(categoria,descripcion);
        reparaciones.add(nueva);
        System.out.println(categoria+" Registrada en "+patente);
    }

    public void registrarMantencion(String detalle, int Nkm){
        ultima_fecha=detalle;
        kilometraje=Nkm;
        System.out.println("Mantencion y datos actualizados!!");
    }

    public void mostrarCitasYReparaciones(){
        System.out.println("\n--- Vehiculo de " + nombre_dueno + " (" + marca + " " + modelo + " - " + patente + ") ---");
        System.out.println("Citas:");
        if (citas.isEmpty()){
            System.out.println("  No hay citas registradas.");
        } else {
            for (int i=0;i<citas.size();i++){
                System.out.println("  " + (i+1) + ". " + citas.get(i));
            }
        }
        
        System.out.println("Reparaciones:");
        if (reparaciones.isEmpty()){
            System.out.println("  No hay reparaciones pendientes.");
        } else {
            for (int i=0;i<reparaciones.size();i++){
                System.out.println("  " + (i+1) + ". " + reparaciones.get(i));
            }
        }
    }
    
    @Override
    public String toString(){
        return "Dueño del vehiculo: "+nombre_dueno+"\n"+
                "Patente: "+patente+"\n"+
                "Marca: "+marca+"\n"+
                "kilometaje: "+kilometraje+"\n"+
                "Ultima revision: "+ultima_fecha;
    }
    
}
