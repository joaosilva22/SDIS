package protocols;

import main.DistributedBackupService;
import utils.IOUtils;
import communications.Message;
import communications.MessageHeader;
import communications.MessageConstants;
import files.FileManager;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.IOException;

public class FileDeletionSubprotocol {
    private FileManager fileManager;
    private String mcAddr;
    private int mcPort;
    private int serverId;
    
    public FileDeletionSubprotocol(DistributedBackupService service) {
        serverId = service.getServerId();
        fileManager = service.getFileManager();
        mcAddr = service.getMcAddr();
        mcPort = service.getMcPort();
    }

    public void initDelete(float version, int senderId, String fileId) {
        IOUtils.log("Initiating DELETE <" + fileId + ">");
        fileManager.deleteFile(fileId);

        MessageHeader header = new MessageHeader()
            .setMessageType(MessageConstants.MessageType.DELETE)
            .setVersion(version)
            .setSenderId(senderId)
            .setFileId(fileId);

        Message message = new Message()
            .addHeader(header);

        try {
            InetAddress inetaddress = InetAddress.getByName(mcAddr);
            DatagramSocket socket = new DatagramSocket();
            byte[] buf = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, inetaddress, mcPort);
            socket.send(packet);
        } catch (UnknownHostException e) {
            IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
            e.printStackTrace();
        } catch (SocketException e) {
            IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
            e.printStackTrace();
        }

        try {
            fileManager.save(serverId);
        } catch (IOException e) {
            IOUtils.warn("FileDeletionSubprotocol warning: Failed to save metadata" + e.toString());
            e.printStackTrace();
        }
    }

    public void delete(Message request) {
        MessageHeader requestHeader = request.getHeaders().get(0);
        String fileId = requestHeader.getFileId();
        float version = requestHeader.getVersion();
        int senderId = requestHeader.getSenderId();

        IOUtils.log("Received DELETE <" + fileId + ">");

        if (fileManager.getFile(fileId) != null) {
            for (int chunkNo : fileManager.getFileChunks(fileId).keySet()) {
                IOUtils.log("Deleted file " + fileId);
                fileManager.deleteChunkFile(fileId, chunkNo);
                fileManager.addUsedSpace(fileManager.getChunk(fileId, chunkNo).getSize() * -1);
                fileManager.deleteChunk(fileId, chunkNo);
            }
            fileManager.deleteFile(fileId);
        }

        try {
            fileManager.save(serverId);
        } catch (IOException e) {
            IOUtils.warn("FileDeletionSubprotocol warning: Failed to save metadata" + e.toString());
            e.printStackTrace();
        }
    }

    public void initGetlease(float version, int senderId, String fileId, int chunkNo) {
        IOUtils.log("Initiating GETLEASE <" + fileId + ", " + chunkNo + ">");
        boolean done = false;
        int iteration = 0;
        // TODO: Substituir este magic number por uma constante
        int delay = 1000;

        while (!done && iteration <  5) {
            MessageHeader header = new MessageHeader()
                .setMessageType(MessageConstants.MessageType.GETLEASE)
                .setVersion(version)
                .setSenderId(senderId)
                .setFileId(fileId)
                .setChunkNo(chunkNo);

            Message message = new Message()
                .addHeader(header);

            try {
                InetAddress inetaddress = InetAddress.getByName(mcAddr);
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, inetaddress, mcPort);
                socket.send(packet);
            } catch (UnknownHostException e) {
                IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                e.printStackTrace();
            } catch (SocketException e) {
                IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                e.printStackTrace();
            } catch (IOException e) {
                IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                e.printStackTrace();
            }

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                e.printStackTrace();
            }

            if (!fileManager.getChunk(fileId, chunkNo).hasExpired()) {
                done = true;
            } else {
                iteration += 1;
                delay *= 2;
                if (iteration < 5) {
                    IOUtils.warn("Failed to extend lease, retrying <" + fileId + ", " + chunkNo + ">");
                } else {
                    IOUtils.warn("Failed to extend lease, aborting <" + fileId + ", " + chunkNo + ">");
                }
            }
        }
        if (!done) {
            // TODO: Eliminar o chunk
            IOUtils.log("Failed to extend chunk lease, deleting <" + fileId + ", " + chunkNo + ">");
            fileManager.deleteChunkFile(fileId, chunkNo);
            fileManager.addUsedSpace(fileManager.getChunk(fileId, chunkNo).getSize() * -1);
            fileManager.deleteChunk(fileId, chunkNo);
            if (fileManager.getFileChunks(fileId).size() == 0) {
                IOUtils.log("No more chunks remaining from file, deleting : " + fileId);
                fileManager.deleteFile(fileId);
            }
        }
        try {
            fileManager.save(serverId);
        } catch (IOException e) {
            IOUtils.warn("FileDeletionSubprotocol warning: Failed to save metadata" + e.toString());
            e.printStackTrace();
        }
    }

    public void getlease(Message request) {
        MessageHeader requestHeader = request.getHeaders().get(0);
        String fileId = requestHeader.getFileId();
        float version = requestHeader.getVersion();
        int senderId = requestHeader.getSenderId();
        int chunkNo = requestHeader.getChunkNo();

        IOUtils.log("Received GETLEASE <" + fileId + ", " + chunkNo + ">");

        if (fileManager.getChunk(fileId, chunkNo) != null) {
            if (fileManager.getFileMetadataByFileId(fileId) != null) {
                MessageHeader header = new MessageHeader()
                    .setMessageType(MessageConstants.MessageType.LEASE)
                    .setVersion(version)
                    .setSenderId(serverId)
                    .setFileId(fileId)
                    .setChunkNo(chunkNo);

                Message message = new Message()
                    .addHeader(header);

                try {
                    InetAddress inetaddress = InetAddress.getByName(mcAddr);
                    DatagramSocket socket = new DatagramSocket();
                    byte[] buf = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, inetaddress, mcPort);
                    socket.send(packet);
                } catch (UnknownHostException e) {
                    IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                    e.printStackTrace();
                } catch (SocketException e) {
                    IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                    e.printStackTrace();
                } catch (IOException e) {
                    IOUtils.err("FileDeletionSubprotocol error: " + e.toString());
                    e.printStackTrace();
                }
            }
        }
        try {
            fileManager.save(serverId);
        } catch (IOException e) {
            IOUtils.warn("FileDeletionSubprotocol warning: Failed to save metadata" + e.toString());
            e.printStackTrace();
        }
    }

    public void lease(Message request) {
        MessageHeader requestHeader = request.getHeaders().get(0);
        String fileId = requestHeader.getFileId();
        float version = requestHeader.getVersion();
        int senderId = requestHeader.getSenderId();
        int chunkNo = requestHeader.getChunkNo();

        IOUtils.log("Received LEASE <" + fileId + ", " + chunkNo + ">");

        if (fileManager.getChunk(fileId, chunkNo) != null) {
            fileManager.extendChunkLease(fileId, chunkNo);
        }
        try {
            fileManager.save(serverId);
        } catch (IOException e) {
            IOUtils.warn("FileDeletionSubprotocol warning: Failed to save metadata" + e.toString());
            e.printStackTrace();
        }
    }
}
