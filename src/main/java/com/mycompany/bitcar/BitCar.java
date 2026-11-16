/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.bitcar;
import java.util.Scanner;
/**
 *
 * @author Fernando
 */
public class BitCar {

    public static void main(String[] args) {
        Familia familia=new Familia();
        Scanner teclado = new Scanner (System.in);
        int opcion=0;
        
        //Menu visual
        while (opcion!=10){
            
            System.out.println("\n=== BITCAR - PANEL FAMILIAR ===");
            System.out.println("1. Ver integrantes y vehículos");
            System.out.println("2. Añadir miembro a la familia");
            System.out.println("3. Quitar miembro de la familia");
            System.out.println("4. Registrar revisión mecánica a un auto");
            System.out.println("5. Crear informe de revisión ");
            System.out.println("6. Agendar cita");
            System.out.println("7. Agregar reparación o repuesto pendiente");
            System.out.println("8. Ver y gestionar mis citas y reparaciones");
            System.out.println("9. Ver citas y reparaciones de la familia");
            System.out.println("10. Salir");
            System.out.println("\n"); 
            System.out.print("Elige una opción: ");
            
            opcion=teclado.nextInt();
            teclado.nextLine(); 
            
            switch (opcion) {
                case 1->System.out.println(familia+"\n");
                
                case 2->{
                        System.out.println("Nombre: ");
                        String dueno=teclado.nextLine();
                        System.out.println("Patente");
                        String pat=teclado.nextLine();
                        System.out.println("Marca");
                        String marca=teclado.nextLine();
                        System.out.println("Modelo");
                        String modelo=teclado.nextLine();
                        System.out.println("Kilomertaje actual");
                        int km=teclado.nextInt();
                        teclado.nextLine();
                        Vehiculo nuevo=new Vehiculo(dueno,pat,marca,modelo,km);
                        familia.AñadirMiembro(nuevo);
                    }
                
                case 3->{
                        System.out.println("Ingrese patente del vehiculo a eliminar: ");
                        String pat=teclado.nextLine();
                        familia.QuitarMiembro(pat);
                    }
                
                case 4->{
                        Vehiculo V=null;
                        System.out.println("Ingrese patente del vehiculo a buscar: ");
                        String pat=teclado.nextLine();
                        V=familia.BuscarPorPatente(pat);
                        if (V!=null){
                            System.out.println("Detalle de la revision \n");
                            String detalle=teclado.nextLine();
                            System.out.println("Ingrese nuevo kilometraje: ");
                            int nKM=teclado.nextInt();
                            teclado.nextLine();
                            
                            V.registrarMantencion(detalle,nKM);
                        }else{
                            System.out.println("Vehiculo no encontrado");
                        }                          
                }
                
                case 5->{
                        System.out.println("Ingrese patente de vehiculo a crear informe");
                        String pat=teclado.nextLine();
                        familia.CrearInforme(pat);
                    }
                
                case 6->{
                        System.out.println("Tipo de servicio: ");
                        String servicio=teclado.nextLine();
                        System.out.println("Fecha: ");
                        String fecha=teclado.nextLine();
                        System.out.println("Hora: ");
                        String hora=teclado.nextLine();
                        
                        familia.administrador.AgendarCita(servicio, fecha, hora);
                    }
                
                case 7->{
                        System.out.println("Categoria (Reparacion / Revision / Repuesto): ");
                        String cat=teclado.nextLine();
                        System.out.println("Descripcion: ");
                        String desc=teclado.nextLine();
                        
                        familia.administrador.AgregarReparacion(cat, desc);
                    }
                
                case 8->{
                        familia.administrador.mostrarCitasYReparaciones();
                        System.out.println("\nMarcar completado (1=Cita, 2=Reparacion, 0=Salir): ");
                        int op=teclado.nextInt();
                        teclado.nextLine();
                        if (op==1){
                            System.out.println("Numero de cita: ");
                            int num=teclado.nextInt()-1;
                            teclado.nextLine();
                            if (num>=0 && num<familia.administrador.citas.size()){
                                familia.administrador.citas.get(num).Completada();
                            }
                        }else if (op==2){
                            System.out.println("Numero de reparacion: ");
                            int num=teclado.nextInt()-1;
                            teclado.nextLine();
                            if (num>=0 && num<familia.administrador.reparaciones.size()){
                                familia.administrador.reparaciones.get(num).Completada();
                            }
                        }
                    }
                
                case 9 -> familia.verPendientesFamilia();
                
                case 10->System.out.println("Cerrando aplicacion");
                default->System.out.println("opcion invalida");
            }
        }
    }
}