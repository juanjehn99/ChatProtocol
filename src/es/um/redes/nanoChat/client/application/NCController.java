package es.um.redes.nanoChat.client.application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import es.um.redes.nanoChat.client.comm.NCConnector;
import es.um.redes.nanoChat.client.shell.NCCommands;
import es.um.redes.nanoChat.client.shell.NCShell;
import es.um.redes.nanoChat.directory.connector.DirectoryConnector;
import es.um.redes.nanoChat.server.roomManager.NCRoomDescription;

public class NCController {
	//Diferentes estados del cliente de acuerdo con el autómata
	private static final byte PRE_CONNECTION = 1;
	private static final byte PRE_REGISTRATION = 2;
	private static final byte REGISTERED = 3;
	private static final byte IN_ROOM = 4;
	//Código de protocolo implementado por este cliente
	//TODO Cambiar para cada grupo
	private static final int PROTOCOL = 46610755;
	//Conector para enviar y recibir mensajes del directorio
	private DirectoryConnector directoryConnector;
	//Conector para enviar y recibir mensajes con el servidor de NanoChat
	private NCConnector ncConnector;
	//Shell para leer comandos de usuario de la entrada estándar
	private NCShell shell;
	//Último comando proporcionado por el usuario
	private byte currentCommand;
	//Nick del usuario
	private String nickname;
	//Sala de chat en la que se encuentra el usuario (si está en alguna)
	private String room;
	//Mensaje enviado o por enviar al chat
	private String chatMessage;
	//Dirección de internet del servidor de NanoChat
	private InetSocketAddress serverAddress;
	//Estado actual del cliente, de acuerdo con el autómata
	private byte clientStatus = PRE_CONNECTION;

	//Constructor
	public NCController() {
		shell = new NCShell();
	}

	//Devuelve el comando actual introducido por el usuario
	public byte getCurrentCommand() {		
		return this.currentCommand;
	}

	//Establece el comando actual
	public void setCurrentCommand(byte command) {
		currentCommand = command;
	}

	//Registra en atributos internos los posibles parámetros del comando tecleado por el usuario
	public void setCurrentCommandArguments(String[] args) {
		//Comprobaremos también si el comando es válido para el estado actual del autómata
		switch (currentCommand) {
		case NCCommands.COM_NICK:
			if (clientStatus == PRE_REGISTRATION)
				nickname = args[0];
			break;
		case NCCommands.COM_ENTER:
			room = args[0];
			break;
		case NCCommands.COM_SEND:
			chatMessage = args[0];
			break;
		default:
		}
	}

	//Procesa los comandos introducidos por un usuario que aún no está dentro de una sala
	public void processCommand() throws IOException {
		switch (currentCommand) {
		case NCCommands.COM_NICK:
			if (clientStatus == PRE_REGISTRATION) {
				registerNickName();
				clientStatus = REGISTERED;
			}
			else
				System.out.println("* You have already registered a nickname ("+nickname+")");
			break;
		case NCCommands.COM_ROOMLIST:
			if (clientStatus == REGISTERED) {
				getAndShowRooms();
			}
			else {
				System.out.println("* You have to be registered to see rooms.");
			}
			//TODO LLamar a getAndShowRooms() si el estado actual del autómata lo permite
			//TODO Si no está permitido informar al usuario
			break;
		case NCCommands.COM_ENTER:
			//TODO LLamar a enterChat() si el estado actual del autómata lo permite
			if (clientStatus != IN_ROOM) {
				enterChat();
				clientStatus = IN_ROOM;
			}
			else {
				System.out.println("* Can't get in a room if you are already in one.");
			}
			//TODO Si no está permitido informar al usuario
			break;
		case NCCommands.COM_QUIT:
			//Cuando salimos tenemos que cerrar todas las conexiones y sockets abiertos
			ncConnector.disconnect();			
			directoryConnector.close();
			break;
		default:
		}
	}
	
	//Método para registrar el nick del usuario en el servidor de NanoChat
	private void registerNickName() {
		try {
			//Pedimos que se registre el nick (se comprobará si está duplicado)
			boolean registered = ncConnector.registerNickname(nickname);
			//TODO: Cambiar la llamada anterior a registerNickname() al usar mensajes formateados 
			if (registered) {
				//TODO Si el registro fue exitoso pasamos al siguiente estado del autómata
				System.out.println("* Your nickname is now "+nickname);
				clientStatus = REGISTERED;
			}
			else {
				//En este caso el nick ya existía
				System.out.println("* The nickname is already registered. Try a different one.");
				clientStatus = PRE_REGISTRATION;
			}
		} catch (IOException e) {
			System.out.println("* There was an error registering the nickname");
		}
	}

	//Método que solicita al servidor de NanoChat la lista de salas e imprime el resultado obtenido
	private void getAndShowRooms() throws IOException {
		//TODO Lista que contendrá las descripciones de las salas existentes
		//TODO Le pedimos al conector que obtenga la lista de salas ncConnector.getRooms()
		 ArrayList<NCRoomDescription> listaSalas = ncConnector.getRooms();
		 for(NCRoomDescription elem : listaSalas) {
			 System.out.println(elem);
		 }
		//TODO Una vez recibidas iteramos sobre la lista para imprimir información de cada sala
	}

	//Método para tramitar la solicitud de acceso del usuario a una sala concreta
	private void enterChat() throws IOException {
		//TODO Se solicita al servidor la entrada en la sala correspondiente ncConnector.enterRoom()
		boolean booleanoEntrada = ncConnector.enterRoom(room);
		if (booleanoEntrada == false) {
			System.out.println("* Couldn't get in the room, try again.");
		}
		else {
			System.out.println("* You are in the room, be kind to people.");
			clientStatus = IN_ROOM;
			do {
				//Pasamos a aceptar sólo los comandos que son válidos dentro de una sala
				readRoomCommandFromShell();
				processRoomCommand();
			} while (currentCommand != NCCommands.COM_EXIT);
		}
		//TODO Si la respuesta es un rechazo entonces informamos al usuario y salimos
		//TODO En caso contrario informamos que estamos dentro y seguimos
		//TODO Cambiamos el estado del autómata para aceptar nuevos comandos
		
		System.out.println("* Your are out of the room");
		clientStatus = REGISTERED;
		//TODO Llegados a este punto el usuario ha querido salir de la sala, cambiamos el estado del autómata
	}

	//Método para procesar los comandos específicos de una sala
	private void processRoomCommand() throws IOException {
		switch (currentCommand) {
		case NCCommands.COM_ROOMINFO:
			//El usuario ha solicitado información sobre la sala y llamamos al método que la obtendrá
			getAndShowInfo();
			break;
		case NCCommands.COM_SEND:
			//El usuario quiere enviar un mensaje al chat de la sala
			sendChatMessage();
			break;
		case NCCommands.COM_SOCKET_IN:
			//En este caso lo que ha sucedido es que hemos recibido un mensaje desde la sala y hay que procesarlo
			processIncommingMessage();
			break;
		case NCCommands.COM_EXIT:
			//El usuario quiere salir de la sala
			exitTheRoom();
		}		
	}

	//Método para solicitar al servidor la información sobre una sala y para mostrarla por pantalla
	private void getAndShowInfo() throws IOException {
		//TODO Pedimos al servidor información sobre la sala en concreto
		NCRoomDescription info =  ncConnector.getRoomInfo(room);
		System.out.println(info);
		//TODO Mostramos por pantalla la información
	}

	//Método para notificar al servidor que salimos de la sala
	private void exitTheRoom() {
		//TODO Mandamos al servidor el mensaje de salida
		clientStatus = REGISTERED;
		//TODO Cambiamos el estado del autómata para indicar que estamos fuera de la sala
	}

	//Método para enviar un mensaje al chat de la sala
	private void sendChatMessage() {
		//TODO Mandamos al servidor un mensaje de chat
	}

	//Método para procesar los mensajes recibidos del servidor mientras que el shell estaba esperando un comando de usuario
	private void processIncommingMessage() {		
		//TODO Recibir el mensaje
		//TODO En función del tipo de mensaje, actuar en consecuencia
		//TODO (Ejemplo) En el caso de que fuera un mensaje de chat de broadcast mostramos la información de quién envía el mensaje y el mensaje en sí
	}

	//MNétodo para leer un comando de la sala 
	public void readRoomCommandFromShell() {
		//Pedimos un nuevo comando de sala al shell (pasando el conector por si nos llega un mensaje entrante)
		shell.readChatCommand(ncConnector);
		//Establecemos el comando tecleado (o el mensaje recibido) como comando actual
		setCurrentCommand(shell.getCommand());
		//Procesamos los posibles parámetros (si los hubiera)
		setCurrentCommandArguments(shell.getCommandArguments());
	}

	//Método para leer un comando general (fuera de una sala)
	public void readGeneralCommandFromShell() {
		//Pedimos el comando al shell
		shell.readGeneralCommand();
		//Establecemos que el comando actual es el que ha obtenido el shell
		setCurrentCommand(shell.getCommand());
		//Analizamos los posibles parámetros asociados al comando
		setCurrentCommandArguments(shell.getCommandArguments());
	}

	//Método para obtener el servidor de NanoChat que nos proporcione el directorio
	public boolean getServerFromDirectory(String directoryHostname) {
		//Inicializamos el conector con el directorio y el shell
		System.out.println("* Connecting to the directory...");
		//Intentamos obtener la dirección del servidor de NanoChat que trabaja con nuestro protocolo
		try {
			
			directoryConnector = new DirectoryConnector(directoryHostname);
			serverAddress = directoryConnector.getServerForProtocol(PROTOCOL);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			serverAddress = null;
		}
		//Si no hemos recibido la dirección entonces nos quedan menos intentos
		if (serverAddress == null) {
			System.out.println("* Check your connection, the directory is not available.");		
			return false;
		}
		else return true;
	}
	
	//Método para establecer la conexión con el servidor de Chat (a través del NCConnector)
	public boolean connectToChatServer() {
			try {
				//Inicializamos el conector para intercambiar mensajes con el servidor de NanoChat (lo hace la clase NCConnector)
				ncConnector = new NCConnector(serverAddress);
			} catch (IOException e) {
				System.out.println("* Check your connection, the game server is not available.");
				serverAddress = null;
			}
			//Si la conexión se ha establecido con éxito informamos al usuario y cambiamos el estado del autómata
			if (serverAddress != null) {
				System.out.println("* Connected to "+serverAddress);
				clientStatus = PRE_REGISTRATION;
				return true;
			}
			else return false;
	}

	//Método que comprueba si el usuario ha introducido el comando para salir de la aplicación
	public boolean shouldQuit() {
		return currentCommand == NCCommands.COM_QUIT;
	}

}
