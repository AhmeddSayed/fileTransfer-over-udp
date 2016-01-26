/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Server_side;

import fileReader.folder;
import java.io.*;
import static java.lang.Integer.parseInt;
import static java.lang.Math.pow;
import java.net.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UDP_Server {

    public DatagramSocket serverSocket;
    public folder theFolder = new folder();

    // array that holds the clients that are connected, to remember which packet
    // was sent to which client
    List<Client_Connected> clients = new ArrayList();

    // mode is passed from the main class, and decides which
    // implementation to use:
    // 
    // 0 ---> Stop and Wait
    // 1 ---> Selective Repeat
    private int mode;

    public UDP_Server(int mode) {
        this.mode = mode;

        try {
            serverSocket = new DatagramSocket(9876);
        } catch (SocketException ex) {
            Logger.getLogger(UDP_Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("UDP Server ONLINE");
        System.out.println("Waiting For Clients ...");
        this.run();
    }

    public void run() {

        byte[] receiveData;
        while (true) {
            receiveData = new byte[512];
            // declaring the packet that we will receive the data into
            DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                // setting the timeout for the socket
                serverSocket.setSoTimeout(10);

                // receiving the data from client
                serverSocket.receive(receivedPacket);

            } catch (SocketTimeoutException e) {
                // waiting for packets timeout
                // check that the existing clients have not timed out
                checkClientsTimeout();

            } catch (IOException ex) {
                break;
            }

            // making sure that we received a packet 
            if (receivedPacket.getAddress() != null) {
                // passing the packet to the function that decides what the data is and what to do with it
                receiveData(receivedPacket);
            }
        }
    }

    private void receiveAck(Client_Connected theClient, DatagramPacket thePacket) {
        // ripping the data from the packet and converting to string
        String data = new String(thePacket.getData()).trim();
        // Ack data is sent in the format: Ack:packetID
        // Example --> Ack:313
        // ripping the packet id from the Ack data
        if (data.split(":")[1].contains("404X")) {
            // case that it's an Ack for a 404 
            System.out.println("Received an Ack from " + theClient.getIP().toString() + " for packet: 404");
            // remove current client from the list
            this.clients.remove(theClient);
        } else {
            if (data.split(":")[1].length() == 4) {

                int packetID = parseInt(data.split(":")[1]);
                // adding the Ack to the list of acks
                System.out.println("Received an Ack from " + theClient.getIP().toString() + " for packet: " + packetID);
                theClient.addAck(packetID);
                

                // checking if we didn't send all the file yet
                if (theClient.getLastGoodFileIndex() == theClient.getEndOfFileIndex()) {

                    System.out.println("File sent to " + theClient.getIP());
                    System.out.println("--------------------------------------------");
                    this.clients.remove(theClient);
                    return;

                } else {

                    check_Response(theClient);
                }

            }
        }

    }

    private void receiveData(DatagramPacket thePacket) {
        // ripping the data from the packet and converting to string
        String data = new String(thePacket.getData()).trim();
        // ripping the IPAddress of the client
        InetAddress IPAddress = thePacket.getAddress();
        // ripping the port that the client uses for communication
        int port = thePacket.getPort();
        // the flag that decides either if the client has already connected
        // before and exists in the list, or is a new  client and needs to be added
        boolean isNew = true;

        // the current client connected to the server
        Client_Connected theClient = null;

        // checking if client is already connected, if not, then add to the list
        for (Client_Connected aClient : this.clients) {

            if (aClient.getIP().equals(IPAddress) && aClient.getPort() == port) {
                isNew = false;
                theClient = aClient;
                break;
            }
        }

        if (isNew == true) {
            // normal data packet, typically will be during the first
            // time of communication and will hold the filename that the
            // cliend wants.
            // data format that the client sends is (fileName: theFileName)
            // splitting the data fileName:blabla will result in an array of 
            // two elements (fileName, theFileName) we want the second element:
            String fileName = data.split(":")[1].trim();

            System.out.println(IPAddress + " is connected.");
            System.out.println(IPAddress + " asks for the file: " + fileName);

            // add client to the clients list
            theClient = new Client_Connected(fileName, IPAddress, port);
            this.clients.add(theClient);

            try {
                sendData(theClient);
            } catch (IOException ex) {
                Logger.getLogger(UDP_Server.class
                        .getName()).log(Level.SEVERE, null, ex);

            }
        } else {
            receiveAck(theClient, thePacket);
        }
    }

    private void sendData(Client_Connected theClient) throws IOException {
        // checking if the file exists
        boolean fileExists = theFolder.checkFile(theClient.getFileName());
        // holds packet id for the current packet to be sent
        int packetID;

        if (theClient.getLastSentPacketID() == -1) {
            // no previous packets sent
            packetID = 1;
        } else {
            // previous packets sent
            // increasing the packetID by one
            packetID = theClient.getLastSentPacketID() + 1;
        }

        if (fileExists == false) {
            // file not found
            // send the 404 code to the client
            send404(theClient);
        } else {
            // a valid fileName was given and the file was found
            // reading the file
            File theFile = theFolder.getFile(theClient.getFileName());

            System.out.println("Sending packet " + packetID + " to " + theClient.getIP());
            // data now holds the file in bytes
            byte[] data = Files.readAllBytes(theFile.toPath());

            if (this.mode == 0) {
                // Stop and Wait mode
                sendSAW(theClient, data);

            } else if (this.mode == 1) {
                // Selective repeat mode
                sendSR(theClient, data);
            }
        }

    }

    private void check_Response(Client_Connected theClient) {
        if (theClient.AckReceived(mode) == true) {

            try {
                // call the function to send the files to the client
                sendData(theClient);
            } catch (IOException ex) {
                Logger.getLogger(UDP_Server.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            if (theClient.TimedOut(mode) == true) {
                // client timed out retrying up to 3 times
                // then cancling his request, and removing
                // client from client list
                if (theClient.getNumberOfRetries() == 3) {
                    // we tried 3 times, but client didn't reply
                    // removing the client from the list
                    System.out.println("Client " + theClient.getIP() + " timed out!");
                    this.clients.remove(theClient);
                } else {
                    // retrying increases the number of retries
                    theClient.retry();
                    check_Response(theClient);
                }
            }
        }
    }

    private void send404(Client_Connected theClient) {
        System.out.println("File " + theClient.getFileName() + " not found!");
        System.out.println("Sending 404 to " + theClient.getIP() + ".");
        String Data = "404";
        DatagramPacket sendPacket = new DatagramPacket(Data.getBytes(), Data.getBytes().length, theClient.getIP(), theClient.getPort());

        try {
            // sending the packet to the client
            serverSocket.send(sendPacket);
        } catch (IOException ex) {
            Logger.getLogger(UDP_Server.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        theClient.startTimer();

    }

    private void sendSAW(Client_Connected theClient, byte[] data) {

        byte[] buffer = new byte[512];
        if (theClient.getLastSentPacketID() != -1) {
            // means we sent some packets before
            if (theClient.AckReceived(0) == false) {
                // checking if the client sent an ack for the previous packet
                if (theClient.TimedOut(0) == true) {
                    // client timed out retrying up to 3 times
                    // then cancling his request, and removing
                    // client from client list
                    if (theClient.getNumberOfRetries() >= 3) {
                        // we tried 3 times, but client didn't reply
                        // removing the client from the list
                        this.clients.remove(theClient);
                    } else {
                        // retrying increases the number of retries
                        theClient.retry();
                    }
                }
            } else {
                // we need to check if we didn't send everything yet, -1 means that we
                // have indeed looped through the whole file and sent it
                if (theClient.getFileIndex() == data.length) {
                    // removing the client from the list because we sent him the file
                    this.clients.remove(theClient);
                } else {
                    theClient.setEndOfFileIndex(data.length - 1);
                    buffer = generateData(theClient, data);
                    // received previous ack, send data
                    DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, theClient.getIP(), theClient.getPort());

                    if (getProbability() >= 5 || theClient.getNumberOfRetries() >= 2) {
                        // checking the probabilty of the file being sent to the client
                        // or the case that we tried to send the file twice, and we don't want
                        // to fail trying to send it
                        try {
                            // sending the packet to the client
                            serverSocket.send(sendPacket);
                        } catch (IOException ex) {
                            Logger.getLogger(UDP_Server.class
                                    .getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    theClient.startTimer();
                }
            }
        } else {
            //  we never sent any packets before
            buffer = generateData(theClient, data);
            DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, theClient.getIP(), theClient.getPort());

            if (getProbability() >= 5 || theClient.getNumberOfRetries() >= 2) {
                // checking the probabilty of the file being sent to the client
                // or the case that we tried to send the file twice, and we don't want
                // to fail trying to send it
                try {
                    // sending the packet to the client
                    serverSocket.send(sendPacket);
                } catch (IOException ex) {
                    Logger.getLogger(UDP_Server.class
                            .getName()).log(Level.SEVERE, null, ex);
                }

            }
            theClient.startTimer();

        }
    }

    private void sendSR(Client_Connected theClient, byte[] data) {

    }

    private byte[] generateData(Client_Connected theClient, byte[] fileData) {
        // -1 means no previous sent packets    
        // buffer holds the data that we will send
        byte[] buffer = new byte[512];
        // holds packet id for the current packet to be sent
        int packetID;

        int lastElement = 0;

        if (theClient.getLastSentPacketID() == -1) {
            // no previous packets sent
            packetID = 1;
        } else {
            // previous packets sent
            // increasing the packetID by one
            packetID = theClient.getLastSentPacketID() + 1;
        }
        // data format will be -> ID:packetID:data
        // for IDs we set 4 bytes 
        //example -> I:0034:abcdef 
        // this loop will construct the data that we send 
        // this String will hold the value for each byte we write
        byte aByte;
        String byteString = "";
        for (int i = 0; i < 512; i++) {
            switch (i) {
                case 0:
                    // the first byte of the data format:
                    // ID:1234:data
                    byteString = "I";
                    aByte = byteString.getBytes()[0];
                    break;
                case 1:
                    // the second byte of the data format:
                    // ID:1234:data
                    byteString = "D";
                    aByte = byteString.getBytes()[0];
                    break;
                case 2:
                    // this is the delimiter for the data
                    byteString = ":";
                    aByte = byteString.getBytes()[0];
                    break;

                // making sure that data is in the correct format:
                // example -> 0023 instead of 23
                case 3:
                    byteString = Integer.toString((int) (packetID % pow(10, 4) / pow(10, 3)));
                    aByte = byteString.getBytes()[0];
                    break;
                case 4:
                    byteString = Integer.toString((int) (packetID % pow(10, 3) / pow(10, 2)));
                    aByte = byteString.getBytes()[0];
                    break;
                case 5:
                    byteString = Integer.toString((int) (packetID % pow(10, 2) / pow(10, 1)));
                    aByte = byteString.getBytes()[0];
                    break;
                case 6:
                    byteString = Integer.toString((int) (packetID % pow(10, 1) / pow(10, 0)));
                    aByte = byteString.getBytes()[0];
                    break;
                case 7:
                    // this is the delimiter for the data
                    byteString = ":";
                    aByte = byteString.getBytes()[0];
                    break;
                default:
                    // data from the file goes here

                    // if this was the first packet, then the data should hold only the file size
                    if (theClient.getLastSentPacketID() == -1) {

                        // to avoid outOfBounds pointer exception
                        if (i - 8 >= String.valueOf(fileData.length).split("").length) {
                            byteString = "";
                            aByte = 0;
                            break;
                        }

                        // converting the size of the file to string array
                        byteString = String.valueOf(fileData.length).split("")[i - 8];
                        aByte = byteString.getBytes()[0];
                    } else {
                        // continue where we left off
                        // to avoid outOfBounds  pointer exception
                        if (theClient.getFileIndex() + 1 >= fileData.length) {
                            byteString = "";
                            aByte = 8;
                            break;
                        }
                        // reading the next byte from file
                        aByte = fileData[theClient.getFileIndex() + 1];
                        // setting the new value for the index
                        theClient.setFileIndex(theClient.getFileIndex() + 1);
                    }
                    break;
            }

            // checking if the byte is empty, then we have no more data to print
            // if not, then record the data into the buffer
            if (!byteString.isEmpty()) {
                buffer[i] = aByte;
                lastElement = i;

            } else {
                lastElement = i - 1;
                break;
            }
        }

        theClient.addSentPacket(packetID);

        return buffer;
    }

    private int getProbability() {

        // this method should generate a random number between 0 and 10
        // and return the number
        Random rand = new Random();

        int min = 0;
        int max = 10;
        int randomNum = rand.nextInt((max - min) + 1) + min;

        System.out.println("Probability is " + (float) randomNum / 10);
        return randomNum;
    }

    private void checkClientsTimeout() {
        // this method checks the list of clients connected to server and makes sure
        // that they are not timedout, and if they are timed out, removes them from the list

        // making sure that the list is not empty
        if (!this.clients.isEmpty()) {
            Iterator<Client_Connected> i = this.clients.iterator();
            Client_Connected aClient;

            // looping through the list
            while (i.hasNext()) {
                aClient = i.next();

                // checking if client has timedout
                if (aClient.TimedOut(mode) == true) {
                    // checking if we retried sending the data 3 times before
                    if (aClient.getNumberOfRetries() > 2) {
                        // removing the client from the list
                        i.remove();
                        System.out.println("Sending Failed! :/");
                        System.out.println("--------------------------------------------");
                        break;
                    } else {
                        aClient.retry();
                        try {
                            sendData(aClient);
                        } catch (IOException ex) {
                            Logger.getLogger(UDP_Server.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        break;
                    }
                } else {

                }
            }
        }
    }
}
