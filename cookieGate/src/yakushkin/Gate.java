package yakushkin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

@WebServlet("/Gate")
public class Gate extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private boolean queueInitialized = false;
	
	Map<String, Long> factoryMap; 
	Map<String, Long> cookieMap; 
	
	String renderedLeaderboard;
	
	private Channel channel;
	private Connection connection;
	
	private void renderLeaderboard(){
		renderedLeaderboard = cookieMap
				.entrySet()
				.stream()
				.sorted((a,b)->-Long.compare(a.getValue(), b.getValue()))
				.limit(10)
				.map(e->e.getKey()+":"+e.getValue())
				.collect(Collectors.joining("<br>"));
	}
	
	private Long getCookieCount(String username){
		if (!cookieMap.containsKey(username))
			cookieMap.put(username, (long)0);
		return cookieMap.get(username);
	}

	private Long getFactoryCount(String username){
		if (!factoryMap.containsKey(username))
			factoryMap.put(username, (long)0);
		return factoryMap.get(username);
	}
    @Override
    public void init(){
    	factoryMap = new ConcurrentHashMap<>();
    	cookieMap = new ConcurrentHashMap<>();
    	
    	renderLeaderboard();
    	
    	ConnectionFactory factory = new ConnectionFactory();
    	factory.setHost("localhost");
		try {
			connection = factory.newConnection();
	    	channel = connection.createChannel();
	    	
	    	channel.queueDeclare("events", false, false, false, null);
	    	channel.queueDeclare("updates", false, false, false, null);
	    	

	    	Consumer consumer = new DefaultConsumer(channel) {
	    		  @Override
	    		  public void handleDelivery(String consumerTag, Envelope envelope,
	    		                             AMQP.BasicProperties properties, byte[] body)
	    		      throws IOException {	    		    
	    		    ByteArrayInputStream bos = new ByteArrayInputStream(body);	    		    
	    		    ObjectInputStream ois = new ObjectInputStream(bos);
	    	        try {
						Map<String, String> map = (HashMap) ois.readObject();
						for (Map.Entry<String, String> e : map.entrySet()) {
							String value = e.getValue();
							int delimiter = value.indexOf(';');
							cookieMap.put(e.getKey(), 
									Long.parseLong(value.substring(0, delimiter)));
							factoryMap.put(e.getKey(), 
									Long.parseLong(value.substring(delimiter+1)));
						}
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    	        ois.close();
	    	        
	    	        renderLeaderboard();
	    		  }
	    	};
	    	
	    	channel.basicConsume("updates", true, consumer);
	    	
	    	queueInitialized = true;
		} catch (IOException | TimeoutException e) {			
		}
    }
    
    @Override
    public void destroy(){
    	try {
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    @Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (queueInitialized) {
			try{
				String intention = request.getParameter( "intention" );
				if ("addEvent".equals(intention)) {
					String message = request.getParameter("username");
					if ("factory".equals(request.getParameter("event"))) {
						message += ";-50;;1";
					} else {
						message += ";1;;0";					
					}
					
			    	channel.basicPublish("", "events", null, message.getBytes());
			    	System.out.println(" [x] Sent '" + message + "'");
			    	
				} else if ("getUpdates".equals(intention)) {
					String username = request.getParameter( "username" );
					
					response.getWriter().append(
							getCookieCount(username)+
							";"+getFactoryCount(username)+
							";;"+renderedLeaderboard
					).close();
				}
			} catch(Exception e){
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String sStackTrace = sw.toString(); // stack trace as a string
				response.getWriter().append("0;0;;"+sStackTrace).close();
			}
		} else {
			response.getWriter().append("0;0;;ERROR !queueInitialized").close();
		}
	}


}
