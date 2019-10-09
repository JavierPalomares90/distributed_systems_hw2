import java.net.ServerSocket;
import java.net.Socket;
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
    private static final String RELEASE = "release";
    private static final String ACKNOWLEDGE= "acknowledge";
    private static int serverId = -1;
    private static String ipAddress;
    private static int port;
    private static AtomicInteger logicalClock;
    // The queue of requests to enter critical section
    private static RequestQueue requestQueue;
    private static AtomicBoolean waitToEnterFlag;
    private static AtomicInteger dataVersion;

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
      for (int i = 1; i < n + 1; i++)
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
    dataVersion = new AtomicInteger(0);
    waitToEnterFlag = new AtomicBoolean(false);

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
    requestQueue = new RequestQueue(numServers);


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

  private static class RequestQueue
  {
      private Lock lock = new ReentrantLock();

      private PriorityQueue<Request> requests;
      public RequestQueue(int numServers)
      {
        // Initialize a queue to maintain the requests from servers
        requests = new PriorityQueue<>(numServers,new RequestComparator());
      }

      public void checkForSelfRequest()
      {
          lock.lock();
          // Check if our request queue is at the top of the queue
          if(requests.peek() != null  && requests.peek().serverId == Server.serverId)
          {
              requests.poll();
              // If it is, pop the queue and notify the threads they can enter the CS
              Server.waitToEnterFlag.notifyAll();
          }
          lock.unlock();
      }

      public void add(Request r)
      {
          lock.lock();
          requests.add(r);
          lock.unlock();
      }

      public synchronized void remove(Request r)
      {
          lock.lock();
          requests.remove(r);
          lock.unlock();
      }

      public Request peek()
      {
          Request r = null;
          lock.lock();
          r = requests.peek();
          lock.unlock();
          return r;
      }

      public Request poll()
      {
          Request r = null;
          lock.lock();
          r = requests.poll();
          lock.unlock();
          return r;
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

      @Override
      public boolean equals(Object obj) 
      {
          if (obj == this) 
          {
              return true;
          }
          if (obj == null || obj.getClass() != this.getClass()) 
          {
              return false;
          }

          Request other = (Request) obj;
          // Request are equal if they are made by the same server
          return this.serverId == other.serverId;
      }

      @Override
      public int hashCode()
      {
          int prime = 31;
          int result = 1;
          result = prime * result + serverId;
          return result;
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

       public static Seat fromString(String idStr, String bookedByStr, String bookedStr)
       {
           Seat s = null;
           idStr = idStr.replace("{", "");
           idStr = idStr.replace("}", "");
           idStr = idStr.replace("\"", "");
           idStr = idStr.replace(":", "");
           idStr = idStr.replace("id", "");

           bookedByStr= bookedByStr.replace("{", "");
           bookedByStr= bookedByStr.replace("}", "");
           bookedByStr= bookedByStr.replace("\"", "");
           bookedByStr= bookedByStr.replace(":", "");
           bookedByStr= bookedByStr.replace("bookedBy", "");

           bookedStr= bookedStr.replace("{", "");
           bookedStr= bookedStr.replace("}", "");
           bookedStr= bookedStr.replace("\"", "");
           bookedStr= bookedStr.replace(":", "");
           bookedStr= bookedStr.replace("booked", "");


           int idVal = Integer.parseInt(idStr);
           boolean booked = Boolean.parseBoolean(bookedStr);
           s = new Seat(idVal);
           if(booked)
           {
            s.book(bookedByStr);
           }

           return s;
       }
  }

  private static class MessagePeerThead implements Callable<Integer>
  {
      Peer p;
      String msg;
      public MessagePeerThead(Peer p, String msgToPeer)
      {
          this.p = p;
          this.msg = msgToPeer;
      }

      private void processResponseFromPeer(String msg)
      {
          String[] tokens = msg.split("\\s+");
          if(tokens == null || tokens.length < 1)
          {
              return;
          }
          // received an ack to the request. Check if we need to update seats to newer version
          if(ACKNOWLEDGE.equals(tokens[0]))
          {
              if(tokens.length < 3)
              {
                  return;
              }
              int version = Integer.parseInt(tokens[1]);
              // A peer has a newer version, update to it
              if(version > dataVersion.get())
              {
                  // update our seat list
                  String seatsJson = tokens[2];
                  /**
                   * TODO: Parse the json and update the server's version of the seats
                   */

              }

          }

      }

      private void sendCmdOverTcp(String command, String hostAddress, int port)
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
                  // Process the response
                  processResponseFromPeer(response);
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
      ServerThread serverThread;
      Socket s;

      public ClientWorkerThread(ServerThread thread,Socket s)
      {
          this.s = s;
          this.serverThread = thread;
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
          List<Seat> seats = this.serverThread.getSeats();
          for (Seat s: seats)
          {
              if(s.isBooked() == false)
              {
                  // Book the seat
                  if(s.book(name))
                  {
                      // Upddate the version of the data since we made a modification
                      dataVersion.getAndIncrement();
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
          List<Seat> seats = this.serverThread.getSeats();
          if(seatNum - 1 > seats.size())
          {
              return seatNum + " is not available";
          }
          Seat s = seats.get(seatNum - 1);
          if(s.isBooked() == false)
          {
              if(s.book(name))
              {
                // Upddate the version of the data since we made a modification
                dataVersion.getAndIncrement();
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
          List<Seat> seats = this.serverThread.getSeats();
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
          List<Seat> seats = this.serverThread.getSeats();
          for (Seat s: seats)
          {
              if(s.getBookedBy().equals(name))
              {
                  if(s.freeSeat())
                  {
                       // Upddate the version of the data since we made a modification
                       dataVersion.getAndIncrement();
                      return "" + s.id;
                  }

              }
          }
          return "No reservation found for " + name;
      }

      private void updateSeatList(int version, String msg)
      {
          msg = msg.replace(" {\"seats\":","");
          msg = msg.replace("[","");
          msg = msg.replace("]","");
          String[] tokens = msg.split(",");
          List<Seat> updatedSeats = new ArrayList();
          for (int i = 0; i < tokens.length;i+=0)
          {
              String id = tokens[i];
              i++;
              String bookedBy = tokens[i];
              i++;
              String booked = tokens[i];
              i++;
              Seat s = Seat.fromString(id,bookedBy,booked);
              updatedSeats.add(s);
          }
          serverThread.setSeats(version,updatedSeats);
      }

      /**
       * Parse the updated seats
       * @param msg
       * @return
       */
      private String update(String msg)
      {
          String[] strs = msg.split("\\s+");
          int version = Integer.parseInt(strs[1]);
          String seatsJson = strs[2];
          updateSeatList(version,seatsJson);
          return "Seats updated successfully";
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
          List<Seat> seats = this.serverThread.getSeats();
          int version = dataVersion.get();
          String seatsJson = getSeatsAsJson(seats);
          // Send the version of the data in the update
          String msg = UPDATE + " " + version + " " + seatsJson;
          try
          {
            msgPeers(msg);
          }catch(InterruptedException e)
          {
              e.printStackTrace();
              return "Unable to update peer's seat list";
          }
          
          return "Peers received updated seat list";
      }

      private String getRequestMsg()
      {
          String request = REQUEST + " " + Server.serverId + " " + Server.logicalClock.get();
           return request;
      }

      private void msgPeers(String msg) throws InterruptedException
      {
          ExecutorService executor = Executors.newFixedThreadPool(5);
          List<Callable<Integer>> workers = new ArrayList<>();
          String self = Server.ipAddress + ":" + Server.port;
          List<Peer> peers = this.serverThread.getPeers();
          for (Peer p:peers)
          {
              // don't send a message to self
              if(self.equals(p.toString()) == false)
              {
                Callable<Integer> worker = new MessagePeerThead(p, msg);
                workers.add(worker);
              }
          }
          executor.invokeAll(workers);
      }

      // Parse a request from peers
      private String request(String[] tokens)
      {
          // Parse the request and add it to the server
          int serverId = Integer.parseInt(tokens[1]);
          int lc = Integer.parseInt(tokens[2]);
          Request r = new Request(serverId,lc);
          Server.requestQueue.add(r);
          Server.requestQueue.checkForSelfRequest();
          List<Seat> seats = this.serverThread.getSeats();
          int version = dataVersion.get();
          String seatsJson = getSeatsAsJson(seats);
          // Acknowledge the request and send our own version of the seats
          String msg = ACKNOWLEDGE + " " + version + " " + seatsJson;
          return msg;
      }

      // Send a request to all peers to enter CS. block until peers send response
      /**
       * TODO: Test that this actually blocks until all the peers respond
       */
      private void sendRequest()
      {
          // Add this request to the queue
          Request r = new Request(Server.serverId,Server.logicalClock.get());
          Server.requestQueue.add(r);
          /**
           * TODO: Test if this is multithreaded safe
           */
          // Send the request to all the peersthe queue
          // TODO: Make sure that this waits for all peers to respond
          String requestMsg = getRequestMsg();
          try
          {
            msgPeers(requestMsg);
          }catch(InterruptedException e)
          {
              e.printStackTrace();
          }
          
      }

      private String release(String[] tokens)
      {
          // Parse the release
          int serverId = Integer.parseInt(tokens[1]);
          Request toRemove = new Request(serverId,0);
          Server.requestQueue.remove(toRemove);
          Server.requestQueue.checkForSelfRequest();
          return "Removed request from " + serverId;
      }


      // Send a release to all peers after exiting CS
      // TODO: Need to test if this is multithreaded safe
      private void sendRelease()
      {
          /**
           * TODO: Test if this is multithreaded safe
           */
          String releaseMsg  = RELEASE + " " + Server.serverId;
          try
          {
            msgPeers(releaseMsg);
          }catch(InterruptedException e)
          {
              e.printStackTrace();
          }
      }

      private void waitToEnter()
      {
          boolean bool = Server.waitToEnterFlag.get();
          // Mark that we're waiting to enter the thread
          if(bool == false)
          {
              Server.waitToEnterFlag.getAndSet(true);
          }
          // Peek the requests and check if our request is at the top of the queue
          if(Server.requestQueue.peek() != null && Server.serverId == Server.requestQueue.peek().serverId)
          {
              return;
          }
          // else wait until out request gets to the top of the queue
          try
          {
              // Wait until another thread notifies us to continue
              waitToEnterFlag.wait();
          }catch (InterruptedException e)
          {
              e.printStackTrace();
              System.err.println("Error waiting for self request to get to top of queue");
          }
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
              response = request(tokens);
          }
          else if (RELEASE.equals(tokens[0]))
          {
              response = release(tokens);
          }
          else
          {

              if(RESERVE.equals(tokens[0]))
              {
                 // Send the request to enter the CS. Block until every peer has responded
                 sendRequest();
                 waitToEnter();
                 response = reserve(tokens);
                 // Update peers of the new seats
                 updatePeers();
                 // Send a release to the peers
                 sendRelease();
              }
              else if (BOOK_SEAT.equals(tokens[0]))
              {
                  sendRequest();
                  waitToEnter();
                  response = bookSeat(tokens);
                  updatePeers();
                  sendRelease();
              }
              else if (SEARCH.equals(tokens[0]))
              {
                  sendRequest();
                  waitToEnter();
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
                  waitToEnter();
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
      public TcpClientWorkerThread(ServerThread serverThread, Socket s)
      {
          super(serverThread,s);
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
      AtomicBoolean isRunning;
      List<Peer> peers;
      Lock threadLock;

      public ServerThread(int port, List<Seat> seats,List<Peer> peers)
      {
          this.port = port;
          this.seats = seats;
          this.peers = peers;
          this.isRunning = new AtomicBoolean(false);
          this.threadLock = new ReentrantLock();
      }

      public void setSeats(int version, List<Seat> updatedList)
      {
          threadLock.lock();
          this.seats = updatedList;
          Server.dataVersion.set(version);
          threadLock.unlock();
      }

      public List<Seat> getSeats()
      {
          List<Seat> toReturn;
          threadLock.lock();
          toReturn = this.seats;
          threadLock.unlock();
          return toReturn;
      }

      public List<Peer> getPeers()
      {
          return this.peers;
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
                      ClientWorkerThread t = new TcpClientWorkerThread(this,socket);
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
