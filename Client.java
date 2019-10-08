import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class Client {

    private static final String RESERVE = "reserve";
    private static final String BOOKSEAT = "bookSeat";
    private static final String SEARCH = "search";
    private static final String DELETE = "delete";

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
        int seatNumber;
        try
        {
            seatNumber = Integer.parseInt(tokens[2]);
        }catch(NumberFormatException e)
        {
            System.err.println("Unable to parse seat number");
            e.printStackTrace();
            return null;
        }

        String cmd = "bookSeat " + userName + " " + seatNumber;
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

    private static void sendCmdOverTcp(String command, List<InetSocketAddress> hosts)
    {
        int numHosts = hosts.size();
        int numTries = 1;
        boolean connect = false;
        // Send the purchase over TCP
        Socket tcpSocket = null;

        try
        {
            // Connect to the host, timeout 100ms
            tcpSocket = new Socket();
            while(connect == false) {
                try {
                    tcpSocket.connect(hosts.get(numTries - 1), 100);
                    connect = true;
                } catch (IOException e) {
                    if (numTries < numHosts) {
                        numTries++;
                    } else {
                        System.err.println("No available host.");
                        System.exit(-1);
                    }
                }
            }

            PrintWriter outputWriter = new PrintWriter(tcpSocket.getOutputStream(), true);
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            // Write the purchase message
            outputWriter.write(command + "\n");
            outputWriter.flush();

            // Wait for the response from the server
            String response;

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
        List<InetSocketAddress> hosts = new ArrayList<InetSocketAddress>();
        int numHosts = 0;
        int numLines = 0;
        Scanner sc = new Scanner(System.in);

        while(true){
            if(numLines > numHosts) {
                break;
            }

            String in = sc.nextLine();

            if(numLines == 0) {
                try {
                    numHosts = Integer.parseInt(in);
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Unable to parse number of servers. First argument should be integer number n of servers.");
                    System.exit(-1);
                }
            } else {

                String[] address = in.split(":");
                if(address == null || address.length != 2)
                {
                    System.err.println("ERROR: Invalid host address. Input in format <ip-address>:<port-number>");
                    System.exit(-1);
                }
                InetAddress ip;
                try {
                    hosts.add(new InetSocketAddress(InetAddress.getByName(address[0]), Integer.parseInt(address[1])));
                } catch(UnknownHostException e)
                {
                    System.err.println("ERROR: Invalid IP address.");
                    System.exit(-1);
                }
            }

            numLines++;
        }

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
                sendCmdOverTcp(command,hosts);
            }

        }
    }
}
