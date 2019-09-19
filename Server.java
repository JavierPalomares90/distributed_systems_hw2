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
    private static final String RESERVE = "reserve";
    private static final String BOOK_SEAT = "bookSeat";
    private static final String SEARCH = "search";
    private static final String DELETE = "delete";

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
    Peer self = null;

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
        if(numLines == serverId)
        {
            // The current line is describing this
            self = p;
        }
      }
      numLines++;
    }
    // get the tcp port for this
    seats = getSeats(numSeats);
    int tcpPort = Integer.parseInt(self.port);

    // Parse messages from clients
    ServerThread tcpServer = new TcpServerThread(tcpPort,seats,serverList);
    new Thread(tcpServer).start();
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

      public boolean freeSeat()
      {
          lock.lock();
          // free the seat
          this.isBooked = false;
          this.bookedBy = null;
          lock.unlock();
          return true;

      }
      
      public boolean isBooked()
      {
          lock.lock();
          boolean value = this.isBooked;
          lock.unlock();
          return value;
      }

      public String getBookedBy()
      {

          lock.lock();
          String value = this.bookedBy;
          lock.unlock();
          return value;
      }

      // Do it in synchronized block
      public boolean book(String name)
      {
          lock.lock();
          boolean isBooked = this.isBooked;
          if(isBooked)
          {
              lock.unlock();
              return false;
          }
          // book the seat
          this.bookedBy = name;
          this.isBooked = true;
          // Release the lock after updating the quantity
          lock.unlock();
          return true;
      }
  }

  private static abstract class ClientWorkerThread implements  Runnable
  {
      Socket s;
      List<Seat> seats;
      List<Peer> peers;

      public ClientWorkerThread(Socket s, List<Seat> seats, List<Peer> peers)
      {
          this.s = s;
          this.seats = seats;
          this.peers = peers;
      }

      private String reserve(String[] tokens)
      {
          if(tokens == null || tokens.length < 2 )
          {
              return null;
          }
          String name = tokens[1];
          // Check for a reservation against this name
          String search = search(tokens);
          if(search != null)
          {
              return "Seat already booked against the name provided";
          }
          for (Seat s: seats)
          {
              if(s.isBooked() == false)
              {
                  // Book the seat
                  if(s.book(name))
                  {
                      return "Seat assigned to you is " + s.id;
                  }
              }
          }
          return "Sold out - No seat available";
      }

      private String bookSeat(String[] tokens)
      {
          if(tokens == null || tokens.length < 3 )
          {
              return null;
          }
          String name = tokens[1];
          int seatNum = Integer.parseInt(tokens[2]);
          if(seatNum - 1 > seats.size())
          {
              return seatNum + " is not available";
          }

          Seat s = seats.get(seatNum - 1);
          if(s.isBooked() == false)
          {
              if(s.book(name))
              {
                return "Seat assigned to you is " + s.id;
              }
          }

          return "Seat " + seatNum + " is not available";
      }

      private String search(String[] tokens)
      {
          if(tokens == null || tokens.length < 2 )
          {
              return null;
          }
          String name = tokens[1];
          for (Seat s: seats)
          {
              if(s.getBookedBy().equals(name))
              {
                  return "" + s.id;
              }
          }
          return "No reservation found for " + name;
      }

      private String delete(String[] tokens)
      {
          if(tokens == null || tokens.length < 2 )
          {
              return null;
          }
          String name = tokens[1];
          for (Seat s: seats)
          {
              if(s.getBookedBy().equals(name))
              {
                  if(s.freeSeat())
                  {
                      return "" + s.id;
                  }

              }
          }
          return "No reservation found for " + name;
      }


      public String processMessage(String msg)
      {
          String[] tokens = msg.trim().split("\\s+");
          String response = null;
          if(tokens == null || tokens.length < 1)
          {
              return response;
          }
          if(RESERVE.equals(tokens[0]))
          {
              response = reserve(tokens);
          }
          else if (BOOK_SEAT.equals(tokens[0]))
          {
              response = bookSeat(tokens);
          }
          else if (SEARCH.equals(tokens[0]))
          {
              response = search(tokens);
          }
          else if (DELETE.equals(tokens[0]))
          {
              response = delete(tokens);
          }
            return response;
      }


  }

  private static class TcpClientWorkerThread extends ClientWorkerThread
  {
      public TcpClientWorkerThread(Socket s,List<Seat> seats, List<Peer> peers)
      {
          super(s,seats,peers);
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
      List<Peer> peers;

      public ServerThread(int port, List<Seat> seats,List<Peer> peers)
      {
          this.port = port;
          this.seats = seats;
          this.peers = peers;
      }

      public void stop()
      {
          isRunning.getAndSet(false);
      }

  }

  private static class TcpServerThread extends ServerThread
  {
      public TcpServerThread(int port, List<Seat> seats,List<Peer> peers)
      {
          super(port,seats,peers);
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
                      ClientWorkerThread t = new TcpClientWorkerThread(socket,seats,peers);
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
