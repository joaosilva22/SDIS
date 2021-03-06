package communications;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.net.InetAddress;

public class Message {
    private InetAddress senderAddress = null;
    private ArrayList<MessageHeader> headers = new ArrayList<>();
    private MessageBody body;

    public Message() {}

    public Message(byte[] data) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == MessageConstants.CR && data[i + 1] == MessageConstants.LF) {
                header.write(data[i]);
                header.write(data[i+1]);
                headers.add(new MessageHeader(header.toByteArray()));
                if (data[i + 2] == MessageConstants.CR && data[i + 3] == MessageConstants.LF) {
                    if (i + 4 < data.length) {
                        byte[] bodyBytes = Arrays.copyOfRange(data, i + 4, data.length);
                        body = new MessageBody().setContent(bodyBytes);
                    } else {
                        body = new MessageBody();
                    }
                    break;
                } else {
                    header.reset();
                }
            } else {
                header.write(data[i]);
            }
        }
    }

    public Message addHeader(MessageHeader header) {
        headers.add(header);
        return this;
    }

    public Message setBody(MessageBody body) {
        this.body = body;
        return this;
    }

    public byte[] getBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (MessageHeader header : headers) {
                out.write(header.getBytes());
            }
            out.write(MessageConstants.CRLF.getBytes());
            if (body != null) {
                out.write(body.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public String getMessageType() {
        return headers.get(0).getMessageType();
    }

    public MessageBody getBody() {
        return body;
    }

    public ArrayList<MessageHeader> getHeaders() {
        return headers;
    }

    public void setSenderAddress(InetAddress address) {
        senderAddress = address;
    }

    public InetAddress getSenderAddress() {
        return senderAddress;
    }
}
