import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

  private static final String TCP_MODE = "T";
  private static final String UDP_MODE = "U";
  private static final int BUF_LEN = 1024;
  private static final String PURCHASE = "purchase";
  private static final String CANCEL = "cancel";
  private static final String SEARCH = "search";
  private static final String LIST = "list";
  private static final String SET_MODE ="setmode";

        private static String setMode(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.out.println("Usage: setmode <T|U>");
        return null;
      }
      String mode = tokens[1];
      if(UDP_MODE.equals(mode))
      {
        return UDP_MODE;
      }else
      {
        // default to TCP Protocol
        return TCP_MODE;
      }
  }


  private static String getPurchaseCmd(String[] tokens) 
  {
      if (tokens.length < 4)
      {
        System.err.println("Usage: purchase <user-name> <product-name> <quantity>");
        return null;
      }
      String userName = tokens[1];
      String productName = tokens[2];
      Integer quantity;
      try
      {
        quantity = Integer.parseInt(tokens[3]);
      }catch(NumberFormatException e)
      {
        System.err.println("Unable to parse quantity for purchase order");
        e.printStackTrace();
        return null;
      }
      String cmd = "purchase " + userName + " " + productName + " " + quantity;
      return cmd;
  }

  private static String getCancelCmd(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.err.println("Usage: cancel <order-id>");
        return null;
      }
      String orderId = tokens[1];
      String cmd = "cancel " + orderId;
      return cmd;
  }

  private static String getSearchCmd(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.err.println("Usage: cancel <order-id>");
        return null;
      }
      String userName = tokens[1];
      String cmd = "search " + userName;
      return cmd;

  }

  private static String getListCmd(String[] tokens)
  {
      if (tokens.length < 1)
      {
        System.err.println("Usage: list");
        return null;
      }
      String cmd = "list";
      return cmd;

  }

    private static void sendCmdOverUdp(String command, String hostAddress, int port)
    {
        try
        {
            //Send message
            byte[] payload;
            payload = command.getBytes();
            InetAddress inetAdd = InetAddress.getByName(hostAddress);
            // Let the OS pick a port
            DatagramSocket udpSocket = new DatagramSocket();
            // Send command to udpPort on server
            DatagramPacket sPacket = new DatagramPacket(payload, payload.length, inetAdd, port);
            udpSocket.send(sPacket);

            //Receive Reply:
            byte[] udpBuff = new byte[BUF_LEN];
            DatagramPacket rPacket = new DatagramPacket(udpBuff,udpBuff.length);
            udpSocket.receive(rPacket);
            String msgData = new String(rPacket.getData());
            msgData = msgData.trim();
            // Print the response
            System.out.println(msgData);
            udpSocket.close();
        } catch(Exception e)
        {
            System.err.println("Unable to send message over udp");
            e.printStackTrace();
        }
  }


  private static void sendCmdOverTcp(String command, String hostAddress, int port)
  {
      // Send the purchase over TCP
      Socket tcpSocket = null;
      try
      {
        // Get the socket
        tcpSocket = new Socket(hostAddress, port);
        PrintWriter outputWriter = new PrintWriter(tcpSocket.getOutputStream(), true);
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
        // Write the purchase message
        outputWriter.write(command + "\n");
        outputWriter.flush();

        // Wait for the response from the server
        String response = "";
                            
        while(true)
        {
          response = inputReader.readLine();
          if (response == null)
          {
            break;
          }
          // Print the response
          System.out.println(response);
        }

      }catch(Exception e)
      {
        System.err.println("Unable to send order");
        e.printStackTrace();
      }finally
      {
        if (tcpSocket != null)
        {
          try
          {
            tcpSocket.close();
          }catch(Exception e)
          {
            System.err.println("Unable to close socket");
            e.printStackTrace();
          }

        }
      }
  }

  public static void main (String[] args) 
  {
    String hostAddress;
    int tcpPort;
    int udpPort;
    // Default protocol is TCP
    String ipProtocol = TCP_MODE;


    if (args.length != 3) {
      System.out.println("ERROR: Provide 3 arguments");
      System.out.println("\t(1) <hostAddress>: the address of the server");
      System.out.println("\t(2) <tcpPort>: the port number for TCP connection");
      System.out.println("\t(3) <udpPort>: the port number for UDP connection");
      System.exit(-1);
    }

    hostAddress = args[0];
    tcpPort = Integer.parseInt(args[1]);
    udpPort = Integer.parseInt(args[2]);

    Scanner sc = new Scanner(System.in);
    while(sc.hasNextLine()) {
      String cmd = sc.nextLine();
      String[] tokens = cmd.split("\\s+");

      if (SET_MODE.equals(tokens[0]))
      {
        // Set the ip protocol mode
        String mode = setMode(tokens);
        if(mode != null)
        {
            ipProtocol = mode;
        }

        System.out.println("Setmode to " + ipProtocol);
      } else
      {
        // Send a command to the server
        String command = null;
        if(PURCHASE.equals(tokens[0]))
        {
          command = getPurchaseCmd(tokens);
        } else if (CANCEL.equals(tokens[0]))
        {
          command = getCancelCmd(tokens);
        } else if (SEARCH.equals(tokens[0]))
        {
          command = getSearchCmd(tokens);
        } else if (LIST.equals(tokens[0]))
        {
          command = getListCmd(tokens);
        } else {
          System.err.println("Invalid command: " + tokens[0]);
        }
        // Send the command if it's not null
        if (command != null)
        {
          if(UDP_MODE.equals(ipProtocol))
          {
            sendCmdOverUdp(command,hostAddress,udpPort);
          }else
          {
            sendCmdOverTcp(command,hostAddress,tcpPort);
          }
        }

      }
    }
  }
}
