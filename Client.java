import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

  private static final String TCP_MODE = "T";
  private static final int BUF_LEN = 1024;
  private static final String RESERVE = "reserve";
  private static final String BOOKSEAT = "bookSeat";
  private static final String SEARCH = "search";
  private static final String DELETE = "delete";

  private static String setMode(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.out.println("Usage: setmode <T|U>");
        return null;
      }
      // default to TCP Protocol
      return TCP_MODE;
  }


  private static String getReserveCmd(String[] tokens) 
  {
      if (tokens.length < 2)
      {
        System.err.println("Usage: reserve <name>");
        return null;
      }
      String userName = tokens[1];

      String cmd = "reserve " + userName;
      return cmd;
  }

  private static String getBookSeatCmd(String[] tokens)
  {
      if (tokens.length < 3)
      {
        System.err.println("Usage: bookSeat <name> <seatNum>");
        return null;
      }
      String userName = tokens[1];
      Integer seatNumber;
      try
      {
        seatNumber = Integer.parseInt(tokens[2]);
      }catch(NumberFormatException e)
      {
        System.err.println("Unable to parse seat number");
        e.printStackTrace();
        return null;
      }
      
      String cmd = "bookSeat " + userName + seatNumber;
      return cmd;
  }

  private static String getSearchCmd(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.err.println("Usage: search <name>");
        return null;
      }
      String userName = tokens[1];
    
      String cmd = "search " + userName;
      return cmd;
  }

    private static String getDeleteCmd(String[] tokens)
  {
      if (tokens.length < 2)
      {
        System.err.println("Usage: delete <name>");
        return null;
      }
      String userName = tokens[1];
    
      String cmd = "delete " + userName;
      return cmd;
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
    // Default protocol is TCP
    String ipProtocol = TCP_MODE;


    if (args.length != 2) {
      System.out.println("ERROR: Provide 2 arguments");
      System.out.println("\t(1) <hostAddress>: the address of the server");
      System.out.println("\t(2) <tcpPort>: the port number for TCP connection");
      System.exit(-1);
    }

    hostAddress = args[0];
    tcpPort = Integer.parseInt(args[1]);

    Scanner sc = new Scanner(System.in);
    while(sc.hasNextLine()) {
      String cmd = sc.nextLine();
      String[] tokens = cmd.split("\\s+");

      
      // Send a command to the server
      String command = null;
      if(RESERVE.equals(tokens[0]))
      {
        command = getReserveCmd(tokens);
      } else if (BOOKSEAT.equals(tokens[0]))
      {
        command = getBookSeatCmd(tokens);
      } else if (SEARCH.equals(tokens[0]))
      {
        command = getSearchCmd(tokens);
      } else if (DELETE.equals(tokens[0]))
      {
        command = getDeleteCmd(tokens);
      } else {
        System.err.println("Invalid command: " + tokens[0]);
      }
      // Send the command if it's not null
      if (command != null)
      {
        sendCmdOverTcp(command,hostAddress,tcpPort);
      }
      
    }
  }
}
