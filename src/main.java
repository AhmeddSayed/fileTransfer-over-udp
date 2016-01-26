
import Server_side.UDP_Server;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Ahmed
 */
public class main {

    public static void main(String args[]) {
        // mode is passed from the main class, and decides which
        // implementation to use:
        // 
        // 0 ---> Stop and Wait
        // 1 ---> Selective Repeat
        int mode = 0;

        UDP_Server theServer = new UDP_Server(mode);

    }
}
