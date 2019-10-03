import java.net.ServerSocket;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.security.InvalidParameterException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;


import java.util.Queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.List;
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Scanner;

public class Server
{
    private static final String RESERVE = "reserve";
    private static final String BOOK_SEAT = "bookSeat";
    private static final String SEARCH = "search";
    private static final String DELETE = "delete";
    private static final String UPDATE = "update";
    private static final String REQUEST = "request";
    private static int serverId = -1;
    private static String ipAddress;
    private static int port;
    private static AtomicInteger logicalClock;
    // The queue of requests to enter critical section
    private static PriorityQueue<Request> requests;

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
      /**
       * TODO: If the server goes down and comes back up, it need to get updates from the other servers
       */
    List<Peer> serverList = new ArrayList();
    List<Seat> seats = new ArrayList();
    Scanner sc = new Scanner(System.in);
    int numLines = 0;
    int numServers = 0;
    int numSeats = 0;
    Peer self = null;
    logicalClock = new AtomicInteger(0);

    while(true)
    {
      if (numLines > numServers)
      {
        // Done reading the lines
        break;
      }
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
    ipAddress = self.ipAddress;
    port = self.port;

    // Initialize a queue to maintain the requests from servers
    requests = new PriorityQueue<>(numServers,new RequestComparator());

    // Parse messages from clients
    ServerThread tcpServer = new TcpServerThread(self.port,seats,serverList);
    new Thread(tcpServer).start();
  }

  private static class Peer
  {
      String ipAddress;
      int port = -1;

      public Peer(String ipAddress, String port)
      {
          this.ipAddress = ipAddress;
          try
          {
            this.port = Integer.parseInt(port);
          }catch(NumberFormatException e)
          {
              e.printStackTrace();
          }
      }


       @Override
       public String toString() 
       { 
           if(this.ipAddress != null && this.port != -1)
           {
                return this.ipAddress + ":" + this.port;
           }
           return "";
       } 

  }

  private static class RequestComparator implements Comparator<Request>
  {
      
      public int compare(Request req1, Request req2)
      {
          if(req1 == null)
          {
              if(req2 == null)
              {
                  return 0;
              }
              return -1;
          }
          if(req2 == null)
          {
              return 1;
          }

          int comparison = Integer.compare(req1.logicalTimestamp,req2.logicalTimestamp);
          if(comparison == 0)
          {
              comparison = Integer.compare(req1.serverId, req2.serverId);
          }
          return comparison;
      }

  }

  // Class to represent a request to enter the critical section
  private static class Request
  {
      int serverId;
      int logicalTimestamp;

      public Request(int serverId,int lc)
      {
          this.serverId = serverId;
          this.logicalTimestamp = lc;
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

      @Override
      // To json String
       public String toString() 
       { 
           String json = "{";
           json += "\"id\":\""+ this.id + "\",";
           json += "\"bookedBy\":\"" + (this.getBookedBy() != null? this.getBookedBy():null) + "\",";
           json += "\"booked\":" + (this.isBooked() ? "true":"false") +"}";
           return json;

       } 

       public static Seat fromString()
       {
           Seat s = null;
           /**
            * TODO: Complete impl
            */

           return s;
       }
  }

  private static class UpdatePeerThread implements Callable<Integer>
  {
      Peer p;
      String msg;
      public UpdatePeerThread(Peer p, String updateMsg)
      {
          this.p = p;
          this.msg = updateMsg;
      }

       private static void sendCmdOverTcp(String command, String hostAddress, int port)
        {
            // Send the command over TCP
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
                System.err.println("Unable to send msg to " + hostAddress + ":" + port);
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
  

      public Integer call() throws InvalidParameterException
      {  
          String cmd = this.msg;
          String hostAddress =  this.p.ipAddress;
          int port = this.p.port;
          // Send the command to the peer
          sendCmdOverTcp(cmd, hostAddress, port);
          return 0;
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
              if(s.getBookedBy() != null && s.getBookedBy().equals(name))
              {
                  return "" + s.id;
              }
          }
          return null;
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

      private String joinStringArray(String[] arr, int start, int end, String sep)
      {
          String result = "";
          for (int i = start; i < end - 1; i++)
          {
              result = result + arr[i] + sep;
          }
          result = result + arr[end - 1];
          return result;
      }

      /**
       * Parse the updated seats
       * @param msg
       * @return
       */
      private String update(String msg)
      {
          return "received request to update seats";
          /*
          if(msg == null)
          {
              return null;
          }
          String[] tokens = msg.trim().split("\\r?\\n");
          // Parse the updated seat list
          if(tokens != null)
          {
              // the length of tokens must equal the length of the seats list
              if(seats.size() == tokens.length)
              {
                  for(int i = 0; i < seats.size();i++)
                  {
                      String line = tokens[i];
                      String[] fields = line.split("\\s+");
                      int seatId = Integer.parseInt(fields[0]);
                      Seat s = seats.get(seatId - 1);
                      String bookedBy = null;
                      // Mark the seat as unbooked so it can be updated or remain unbooked
                      s.freeSeat();
                      if(fields.length > 1)
                      {
                          bookedBy = joinStringArray(fields,0,fields.length," ");
                          s.book(bookedBy);
                      }


                  }

              }
            return "Seats successfully updated.";
          }
          System.err.println("Error parsing updated seats msg:" + msg);
          return "Seats not updated.";
          */
      }

      private String getSeatsAsJson(List<Seat> seats)
      {
          String json = "{\"seats\":[";
          for(Seat s: seats)
          {
              json += s.toString() + ",";
          }
          // remove the last comme
          json = json.substring(0, json.length() - 1);
          json += "]}";

          return json;
      }

      // Send the updated seats list to every peer
      private String updatePeers()
      {
          String seatsJson = getSeatsAsJson(seats);
          // Create a pool of 5 threads to send updates to peers
          ExecutorService executor = Executors.newFixedThreadPool(5);
          List<Callable<Integer>> workers = new ArrayList<>();
          String self = Server.ipAddress + ":" + Server.port;
          for (Peer p:peers)
          {
              // don't send a message to self
              if(self.equals(p.toString()) == false)
              {
                Callable<Integer> worker = new UpdatePeerThread(p, UPDATE + " " + seatsJson);
                workers.add(worker);
              }
          }
          try
          {
            executor.invokeAll(workers);
          }catch(InterruptedException ex)
          {
              ex.printStackTrace();
          }
          
          return "Peers received updated seat list";
      }

      private String request(String[] tokens)
      {
          /**
           * TODO: Complete impl
           */
           return null;
      }

      // Send a request to all peers to enter CS. block until peers send response
      // TODO: Need to test if this is multithreaded safe
      private void sendRequest()
      {
          /**
           * TODO: Complete impl and test if this is multithreaded safe
           */
      }


      // Send a release to all peers after exiting CS
      // TODO: Need to test if this is multithreaded safe
      private void sendRelease()
      {
          /**
           * TODO: Complete impl and test if this is multithreaded safe
           */
      }

      public String processMessage(String msg)
      {
          String[] tokens = msg.trim().split("\\s+");
          String response = null;
          if(tokens == null || tokens.length < 1)
          {
              return response;
          }

          if (UPDATE.equals(tokens[0]))
          {
              // Received a update message to update the seat list
              response = update(msg);
          }
          else if (REQUEST.equals(tokens[0]))
          {
              // Received a request to enter the CS from a peer
              request(tokens);
          }
          else
          {

              if(RESERVE.equals(tokens[0]))
              {
                 // Send the request to enter the CS. Block until every peer has responded
                 sendRequest();
                 response = reserve(tokens);
                 // Update peers of the new seats
                 updatePeers();
                 // Send a release to the peers
                 sendRelease();
              }
              else if (BOOK_SEAT.equals(tokens[0]))
              {
                  sendRequest();
                  response = bookSeat(tokens);
                  updatePeers();
                  sendRelease();
              }
              else if (SEARCH.equals(tokens[0]))
              {
                  sendRequest();
                  response = search(tokens);
                  if(response == null)
                  {
                    String name = tokens[1];
                    response = "No reservation found for " + name;
                  }
                  updatePeers();
                  sendRelease();
              }
              else if (DELETE.equals(tokens[0]))
              {
                  sendRequest();
                  response = delete(tokens);
                  updatePeers();
                  sendRelease();
              }

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
          logicalClock.getAndIncrement();
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
                  // Increment the logical clock everytime a client sends a message
                  logicalClock.getAndIncrement();
                  String response = processMessage(msg);
                  // Increment the logical clock on response
                  logicalClock.getAndIncrement();
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
          // increment the logical clock when the thread starts
          logicalClock.getAndIncrement();
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
                      // increment the logical clock everytime a new client connects
                      logicalClock.getAndIncrement();
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
