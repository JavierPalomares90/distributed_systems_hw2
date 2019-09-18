import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server
{

  private static Peer parsePeer(String line)
  {
    // parse the list of servers
    String[] tokens= line.split(":");
    if(tokens == null || tokens.length != 2)
    {
        System.err.println("Invalid line in server file");
        System.exit(-1);
    }
    String ipAddress = tokens[0];
    String port = tokens[1];
    Peer p = new Peer(ipAddress,port);
    return p;
  }

  private static List<Seat> getSeats(int n)
  {
      List<Seat> seats = new ArrayList();
      for (int i =0; i < n; i++)
      {
          Seat s = new Seat(i);
          seats.add(s);
      }
      return seats;
  }

  public static void main (String[] args) {
    List<Peer> serverList = new ArrayList();
    List<Seat> seats = new ArrayList();
    Scanner sc = new Scanner(System.in);
    int numLines = 0;
    int serverId = 0;
    int numServers = 0;
    int numSeats = 0;

    while(true)
    {
      String cmd = sc.nextLine();
      if(numLines == 0)
      {
        String[] tokens = cmd.split("\\s+");
        // parse the first line
        if(tokens == null || tokens.length != 3)
        {
            System.err.println("Invalid line in server file");
            System.exit(-1);
        }
        serverId = Integer.parseInt(tokens[0]);
        numServers = Integer.parseInt(tokens[1]);
        numSeats = Integer.parseInt(tokens[2]);
      }
      else
      {
        if (numLines > numServers)
        {
            // Done reading the lines
            break;
        }
        Peer p = parsePeer(cmd);
        serverList.add(p);
      }
      numLines++;
    }
    seats = getSeats(numSeats);

    // Parse messages from clients
    //ServerThread tcpServer = new TcpServerThread(tcpPort,inventory);
    //new Thread(tcpServer).start();
  }

  private static class Peer
  {
      String ipAddress;
      String port;

      public Peer(String ipAddress, String port)
      {
          this.ipAddress = ipAddress;
          this.port = port;
      }

  }

  private static class Seat
  {
      int id;
      boolean isBooked;
      String bookedBy;
      Lock lock = new ReentrantLock();


      public Seat (int id)
      {
          this.id = id;
          this.isBooked = false;
          this.bookedBy = null;
      }

      public String getBookedBy()
      {

          lock.lock();
          String value = this.bookedBy;
          lock.unlock();
          return value;
      }

      // Do it in synchronized block
      public void book(String name) throws Exception
      {
          lock.lock();
          boolean isBooked = this.isBooked;
          if(isBooked)
          {
              lock.unlock();
              throw new InvalidParameterException(this.id + " is not available.");
          }
          // book the seat
          this.bookedBy = name;
          this.isBooked = true;
          // Release the lock after updating the quantity
          lock.unlock();

      }
  }

  private static abstract class ClientWorkerThread implements  Runnable
  {
      Socket s;
      List<Seat> seats;


      public String processMessage(String msg)
      {
          //TODO: Complete impl
            return null;
      }


  }

  private static class TcpClientWorkerThread extends ClientWorkerThread
  {
      public TcpClientWorkerThread(Socket s,List<Seat> seats)
      {
          this.s = s;
          this.seats = seats;
      }

      public void run()
      {
          // Read the message from the client
          try
          {
              //We have received a TCP socket from the client.  Receive message and reply.
              BufferedReader inputReader = new BufferedReader(new InputStreamReader(s.getInputStream()));
              boolean autoFlush = true;
              PrintWriter outputWriter = new PrintWriter(s.getOutputStream(), autoFlush);
              String inputLine = inputReader.readLine();
              if (inputLine != null && inputLine.length() > 0) {
                  String msg = inputLine;
                  String response = processMessage(msg);
                  if(response != null)
                  {
                      outputWriter.write(response);
                      outputWriter.flush();
                  }
                  outputWriter.close();
              }
          }catch (Exception e)
          {
              System.err.println("Unable to receive message from client");
              e.printStackTrace();
          }finally
          {
              if(s != null)
              {
                  try
                  {
                      s.close();
                  }catch (Exception e)
                  {
                      System.err.println("Unable to close client socket");
                      e.printStackTrace();
                  }
              }

          }

      }

  }
  private static abstract class ServerThread implements Runnable
  {
      int port;
      List<Seat> seats;
      AtomicBoolean isRunning = new AtomicBoolean(false);

      public ServerThread(int port, List<Seat> seats)
      {
          this.port = port;
          this.seats = seats;
      }

      public void stop()
      {
          isRunning.getAndSet(false);
      }

  }

  private static class TcpServerThread extends ServerThread
  {
      public TcpServerThread(int port, List<Seat> seats)
      {
          super(port,seats);
      }

      public void run()
      {
          this.isRunning.getAndSet(true);
          ServerSocket tcpServerSocket = null;
          try
          {
              tcpServerSocket = new ServerSocket(this.port);
              while(this.isRunning.get() == true)
              {
                  Socket socket = null;
                  try
                  {
                      // Open a new socket with clients
                      socket = tcpServerSocket.accept();
                  }catch(Exception e)
                  {
                      System.err.println("Unable to accept new client connection");
                      e.printStackTrace();
                  }
                  if(socket != null)
                  {
                      // Spawn off a new thread to process messages from this client
                      ClientWorkerThread t = new TcpClientWorkerThread(socket,seats);
                      new Thread(t).start();
                  }
              }

          }catch (Exception e)
          {
              System.err.println("Unable to accept client connections");
              e.printStackTrace();
          }finally {
              if (tcpServerSocket != null)
              {
                  try
                  {
                      tcpServerSocket.close();
                  }catch (Exception e)
                  {
                      System.err.println("Unable to close tcp server socket");
                      e.printStackTrace();
                  }
              }
              if (tcpServerSocket != null)
              {
                  try
                  {
                      tcpServerSocket.close();
                      this.isRunning.getAndSet(false);
                  }catch (Exception e)
                  {
                      System.err.println("Unable to close tcp socket");
                      e.printStackTrace();
                  }
              }
          }

      }


  }
}
