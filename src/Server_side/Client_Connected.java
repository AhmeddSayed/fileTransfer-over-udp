/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Server_side;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Ahmed
 */
public class Client_Connected {

    private final String targetFile;
    private final InetAddress IPAddress;
    private final int port;
    private int fileIndex, lastGoodFileIndex, endOfFileIndex;

    private final ArrayList<Integer> Acks = new ArrayList();
    private final ArrayList<Integer> sentPackets = new ArrayList();
    private Timestamp timeStamp;
    private int numberOfRetries = 0;

    public Client_Connected(String fileName, InetAddress IP, int port) {
        this.IPAddress = IP;
        this.port = port;
        this.targetFile = fileName;
        this.Acks.clear();
        this.fileIndex = -1;
        this.lastGoodFileIndex = -1;

    }

    public InetAddress getIP() {
        return this.IPAddress;
    }

    int getPort() {
        return this.port;
    }

    public String getFileName() {
        return this.targetFile;
    }

    public int getFileIndex() {
        return this.fileIndex;
    }

    public void setFileIndex(int newIndex) {
        this.fileIndex = newIndex;
    }

    boolean AckReceived(int mode) {
        if (mode == 0) {
            // stop and wait mode
            if (this.Acks.isEmpty()) {
                // no Acks received, either we sent data and waiting for ack
                // or client timed out and we're retrying
                return false;
            } else {
                for (int ID : this.Acks) {
                    if (this.getLastSentPacketID() == ID) {
                        return true;
                    }
                }
                return false;
            }
        } else {
            // selective repeat mode
            return false;
        }
    }

    boolean TimedOut(int mode
    ) {
        if (mode == 0) {
            if (this.timeStamp == null) {
                return false;
            } else if (this.timeStamp.getNanos() + 500 < new Timestamp(System.currentTimeMillis()).getNanos()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void startTimer() {
        this.timeStamp = new Timestamp(System.currentTimeMillis());

    }

    void addAck(int packetID) {
        this.Acks.add(packetID);
        this.numberOfRetries = 0;
        this.lastGoodFileIndex = this.fileIndex;
    }

    public int getNumberOfRetries() {
        return this.numberOfRetries;
    }

    public void retry() {
        // method that controls the retries for sending packets
        // counting retries

        // to retry we must delete the last sent packet id and resend it again
        if (!this.sentPackets.isEmpty()) {
            this.sentPackets.remove(sentPackets.size() - 1);
        }
        this.fileIndex = this.lastGoodFileIndex;
        this.timeStamp = null;
        this.numberOfRetries++;
        System.out.println("retrying to send to " + this.IPAddress);

    }

    int getLastSentPacketID() {
        if (this.sentPackets.isEmpty()) {
            // no sent packets, client is new
            return -1;
        } else {
            // return the last sent packetID
            return this.sentPackets.get(this.sentPackets.size() - 1);
        }
    }

    public void removePreviousAck() {
        this.Acks.clear();
    }

    public void addSentPacket(int ID) {
        this.sentPackets.add(ID);
    }

    public int getLastGoodFileIndex() {
        return this.lastGoodFileIndex;
    }

    public int getEndOfFileIndex() {
        return this.endOfFileIndex;
    }

    public void setEndOfFileIndex(int index) {
        this.endOfFileIndex = index;
    }
}
