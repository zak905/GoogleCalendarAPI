package com.mymanager.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.mymanager.MyDate;
import com.mymanager.Request;
import com.mymanager.Schedule;
import com.mymanager.TimeSlot;
import com.mymanager.database.RequestCRUD;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;




import org.joda.time.LocalTime;


/* Google Calendar Interface, adds and updates event on Google Calendar */
public class GoogleCalendarManager {
	
	private static final String APPLICATION_NAME = "APPLICATION_NAME";
	
	private static final String USER_ID = "USER_ID";
	
	private static final int EXPIRATION_THRESHOLD = 60;

	  /** Directory to store user credentials. */
	  //Add your the path where you want the credentials stored
	  private static final File DATA_STORE_DIR =
	      new File("src/main/resources/stores/");

	  /**
	   * Global instance of the {@link DataStoreFactory}. 
	   */
	  private static FileDataStoreFactory dataStoreFactory;
	  
	  /** Global instance of the HTTP transport. */
	  private static HttpTransport httpTransport;

	  /** Global instance of the JSON factory. */
	  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	  
	  /** Obtained from Google developpers Console **/
	  
	  private final static String clientId = "your client id";
	  
	  private final static String clientSecret = "your client secret";
	  

	  private static com.google.api.services.calendar.Calendar client;
	  
	  private static CalendarList calendarList;

	  
      /* Constructor , authorizes the application using Oauth2 */
	  public GoogleCalendarManager(){
		  try{
		  httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		  
	      // initialize the data store factory
	      dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
	      
	      Credential credential = null;
	  
	     credential = authorize();
	   
	      if(credential.getExpiresInSeconds() < EXPIRATION_THRESHOLD){
	    	  credential.refreshToken();
	    	  System.out.print("refereshing token: expires in" + credential.getExpiresInSeconds());
	      }
          
	       client = new com.google.api.services.calendar.Calendar.Builder(
	          httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	       
	      
	       initializeCalendarList();

		  }
		  catch(Exception exp){
			  exp.printStackTrace();
		  }
	  }

	  /** Authorizes the installed application to access user's protected data. */
	  private static Credential authorize() throws Exception {
		  
	    // load client secrets
	    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
	        new InputStreamReader(GoogleCalendarManager.class.getResourceAsStream("/client_secret_2.json")));
	   
	    //If the client secrets/Id are not valid
	    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
	        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
	      System.out.println(
	          "Unable to authenticate, Please Enter a Client ID and Secret from https://code.google.com/apis/console/?api=calendar ");
	      System.exit(1);
	    }
	    
	    
	    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	        httpTransport, JSON_FACTORY, clientSecrets,
	        Collections.singleton(CalendarScopes.CALENDAR)).setDataStoreFactory(dataStoreFactory)
	        .build();
	      
	    Credential credential;
	    //First Time
	    if(flow.getCredentialDataStore().isEmpty()) {
	    
	  GoogleAuthorizationCodeRequestUrl url =  flow.newAuthorizationUrl();
	   url.setRedirectUri("urn:ietf:wg:oauth:2.0:oob");
	    System.out.println("Please enter the URL in your browser: " +  url.build().toString());
	    System.out.println("Please enter the code: ");
	    BufferedReader br = 
                new BufferedReader(new InputStreamReader(System.in));

	               String code;
	               code = br.readLine();
	 
		//System.out.println("received code " + code);
		

		AuthorizationCodeTokenRequest accessTokenRequest = flow.newTokenRequest(code);
		accessTokenRequest.setRedirectUri("urn:ietf:wg:oauth:2.0:oob");
		TokenResponse response = accessTokenRequest.execute();
		
	    credential = flow.createAndStoreCredential(response, APPLICATION_NAME);
	    }
	    //After the first time
	    else{
	    	 credential = flow.loadCredential(USER_ID);
	    	
	    }
	    
	    return credential;
	  }
	  
	  /** Adds a new sub Calendar */
	  public void addCalendar(String calendarName) throws Exception{
		Calendar calendar = new Calendar();
		calendar.setSummary(calendarName);
		client.calendars().insert(calendar).execute();
	  }
	  
	  /** Adds an Event to a Calendar */
 public void addSchedule(DateTime startingDateTime, DateTime finishingDateTime, String calendarName) throws Exception{
		  
		  
		  Event event = new Event();
		  event.setDescription("Some Description");
		  event.setSummary("Some Summary");
		  event.setLocation("Event Address");
		  
		  
		   event.setStart(new EventDateTime().setDateTime(startingDateTime));
	       event.setEnd(new EventDateTime().setDateTime(finishingDateTime));
	       
	       
	       addEvent(event, calendarName);
		  
		  
	  }
	  
     
	  
	  
	  
	  private void addEvent(Event event, String calendarName) throws IOException {
		  String calendarId = null;
		  for(int i = 0; i <calendarList.getItems().size(); i++){
			  CalendarListEntry entry = calendarList.getItems().get(i);
			  if(entry.getSummary().equals(calendarName)){
				  calendarId = entry.getId();
			   }
		    }
				  if(calendarId != null){
					  client.events().insert(calendarId, event).execute();
				  }
	    }

	  
	  private void initializeCalendarList() throws Exception{
		  calendarList = client.calendarList().list().execute();
		  
	  }
	  
	  

	
}


