/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.bitcar;

/**
 *
 * @author Fernando
 */
public class Cita {
    public String tipo_servicio;
    public String fecha;
    public String hora;
    public String estado;
    
    public Cita(String ts,String f,String h){
        tipo_servicio=ts;
        fecha=f;
        hora=h;
        estado="Pendiente";
    }
    
    public void Completada(){
        estado="Completado";
    }
    
    @Override
    public String toString(){
        return "Servicio: "+tipo_servicio+" | Fecha: "+fecha+" | Hora: "+hora+" | Estado: "+estado;
    }
    
}