/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.bitcar;

/**
 *
 * @author Fernando
 */
public class Reparacion {
    public String categoria;
    public String descripcion;
    public String estado;
    
    public Reparacion(String c,String d){
        categoria=c;
        descripcion=d;
        estado="Pendiente";
    }
    
    public void Completada(){
        estado="Completada";
        System.out.println(categoria+" Marcada como completada: "+descripcion);
    }
    
    @Override
    public String toString(){
        return "Categoria: "+categoria+" | Descripcion: "+descripcion+" | Estado: "+estado;
    }
}
