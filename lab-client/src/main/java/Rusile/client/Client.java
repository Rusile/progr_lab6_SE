package Rusile.client;

import Rusile.client.CommandDispatcher.CommandListener;
import Rusile.client.CommandDispatcher.CommandToSend;
import Rusile.client.CommandDispatcher.CommandValidators;
import Rusile.client.NetworkManager.ClientSocketChannelIO;
import Rusile.client.NetworkManager.RequestCreator;
import Rusile.common.exception.WrongAmountOfArgumentsException;
import Rusile.common.util.Request;
import Rusile.common.util.Response;
import Rusile.common.util.TextWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.*;

public final class Client {

    private Client() {
        throw new UnsupportedOperationException("This is an utility class and can not be instantiated");
    }

    private static int PORT = 45846;
    private static String HOST;
    private static final int maxPort = 65535;

    private static final Scanner SCANNER = new Scanner(System.in);
    private static Selector selector;
    private static final RequestCreator requestCreator = new RequestCreator();

    private static boolean reconnectionMode = false;
    private static int attempts = 0;


    public static void main(String[] args) {

        try {
            if (!reconnectionMode) {
                inputPort();
            } else {
                Thread.sleep(8 * 1000);
            }
            SocketChannel clientChannel = SocketChannel.open(new InetSocketAddress(HOST, PORT));
            TextWriter.printSuccessfulMessage("Connected!");
            attempts = 0;
            clientChannel.configureBlocking(false);
            selector = Selector.open();
            clientChannel.register(selector, SelectionKey.OP_WRITE);
            startSelectorLoop(clientChannel, SCANNER);
        } catch (ClassNotFoundException e) {
            TextWriter.printErr("Trying to serialize non-serializable object");
        } catch (InterruptedException e) {
            TextWriter.printErr("Thread was interrupt while sleeping. Restart client");
        } catch (UnresolvedAddressException e) {
            TextWriter.printErr("Server with this host not found. Try again");
            main(args);
        } catch (IOException e) {
            TextWriter.printErr("Server is invalid. Trying to reconnect, attempt #" + (attempts + 1));
            reconnectionMode = true;
            if (attempts == 4) {
                TextWriter.printErr("Reconnection failed. Server is dead. Try later...");
                System.exit(1);
            }
            attempts++;
            main(args);
        } catch (NoSuchElementException e) {
            TextWriter.printErr("An invalid character has been entered, forced shutdown!");
            System.exit(1);
        }
    }

    private static void startSelectorLoop(SocketChannel channel, Scanner sc) throws IOException, ClassNotFoundException, InterruptedException {
        do {
            selector.select();
        } while (startIteratorLoop(channel, sc));
    }

    private static boolean startIteratorLoop(SocketChannel channel, Scanner sc) throws IOException, ClassNotFoundException, InterruptedException {
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = readyKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (key.isReadable()) {

                SocketChannel clientChannel = (SocketChannel) key.channel();

                ClientSocketChannelIO socketChannelIO = new ClientSocketChannelIO(clientChannel);
                Response response = (Response) socketChannelIO.receive();

                TextWriter.printInfoMessage(response.getData());

                clientChannel.register(selector, SelectionKey.OP_WRITE);
            } else if (key.isWritable()) {
                try {
                    CommandToSend commandToSend = CommandListener.readCommand(sc);
                    if (commandToSend == null) return false;
                    if (commandToSend.getCommandName().equalsIgnoreCase("execute_script")) {
                        CommandValidators.validateAmountOfArgs(commandToSend.getCommandArgs(), 1);
                        ScriptReader scriptReader = new ScriptReader(commandToSend);
                        startSelectorLoop(channel, new Scanner(scriptReader.getPath()));
                        scriptReader.stopScriptReading();
                        startSelectorLoop(channel, SCANNER);
                    }

                    Request request = requestCreator.createRequestOfCommand(commandToSend);
                    if (request == null) throw new NullPointerException("");
                    SocketChannel client = (SocketChannel) key.channel();

                    ClientSocketChannelIO socketChannelIO = new ClientSocketChannelIO(client);
                    socketChannelIO.send(request);

                    client.register(selector, SelectionKey.OP_READ);
                } catch (NullPointerException | IllegalArgumentException | WrongAmountOfArgumentsException e) {
                    TextWriter.printErr(e.getMessage());
                }


            }

        }
        return true;
    }


    private static void inputPort() {
        TextWriter.printInfoMessage("Enter hostname:");
        try {
            HOST = SCANNER.nextLine();
        } catch (NoSuchElementException e) {
            TextWriter.printErr("An invalid character has been entered, forced shutdown!");
            System.exit(1);
        }
        TextWriter.printInfoMessage("Do you want to use a default port? [y/n]");
        try {
            String s = SCANNER.nextLine().trim().toLowerCase(Locale.ROOT);
            if ("n".equals(s)) {
                TextWriter.printInfoMessage("Please enter the remote host port (1-65535)");
                String port = SCANNER.nextLine();
                try {
                    int portInt = Integer.parseInt(port);
                    if (portInt > 0 && portInt <= maxPort) {
                        PORT = portInt;
                    } else {
                        TextWriter.printErr("The number did not fall within the limits, repeat the input");
                        inputPort();
                    }
                } catch (IllegalArgumentException e) {
                    TextWriter.printErr("Error processing the number, repeat the input");
                    inputPort();
                }
            } else if (!"y".equals(s)) {
                TextWriter.printErr("You entered not valid symbol, try again");
                inputPort();
            }
        } catch (NoSuchElementException e) {
            TextWriter.printErr("An invalid character has been entered, forced shutdown!");
            System.exit(1);
        }
    }
}
