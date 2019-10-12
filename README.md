# Distributed and MultiThreaded Server and Client Demo to Book Seats
## Javier Palomares and Matt Molter
### To compile:
1. Compile `Server.java`
   ```javac Server.java```
2. Compile `Client.java`
    ```javac Client.java```
### To Run
1. Start up each server
   The first line of the input to a server contains three natural numbers separated by a single white-space: `server-id`: server's unique id, `n`: total numbers of server instances, and `z`: the total number of seats in the theater. The numbers of the seats are dened from 1 to z. The next n lines of the server input dene the addresses of all the n servers in the `<ip-address>:<port-number>` format, one per line. The `<ip-address>:<port-number>` of the i-th address line denotes the ip address and port number of server with id i. The crash of a server is simulated by `Ctrl-C`.
2. Start up each client
   A client also accepts its commands by reading standard input. The first line of client input contains the `n`: a natural number that indicates the number of servers present. The next n lines of client input list the ip-addresses, and port of these n servers, one per line in `<ip-address>:<port-number>` form. Their order of appearance in client input denes the server proximity to this client, and the client must connect to servers in this order.

The remainder of the client input contains seat reservation and return commands that should be executed by the client in order of their appearance. The format of these commands is one of the following:

* `reserve <name>` { inputs the name of a person and reserves a seat against this name. The client sends this command to the server. If the theater does not have enough seats(completely booked), no seat is assigned and the command responds with message: 'Sold out - No seat available'. If a
reservation has already been made under that name, then the command responds with message: 'Seat already booked against the name provided'. Otherwise, a seat is reserved against the name provided and the client is relayed a message: 'Seat assigned to you is `<seat-number>`'.

* `bookSeat <name> <seatNum>` { behaves similar to reserve command, but imposes an additional constraint that a seat is reserved if and only if there is no existing reservation against name and the seat having the number `<seatNum>` is available. If there is no existing reservation but `<seatNum>` is not available, the response is: `<seatNum>` is not available'.

* `search <name>` { returns the seat number reserved for name. If no reservation is found for name the system responds with a message: 'No reservation found for `<name>`'.

* `delete <name>` { frees up the seat allocated to that person. The command returns the seat number that was released. If no existing reservation was found responds with: 'No reservation found for `<name>`'.

### Example input
Here is a small example of the inputs.
Inputs

#### server1 input:
```
1 2 10
127.0.0.1:8025
127.0.0.1:8030
```
#### server2 input:
```
2 2 10
127.0.0.1:8025
127.0.0.1:8030
```
#### client1 input:
```
2
127.0.0.1:8025
127.0.0.1:8030
reserve Bob
delete Mary
search John
```
#### client2 input:
```
2
127.0.0.1:8030
127.0.0.1:8025
bookSeat Alice 8
```