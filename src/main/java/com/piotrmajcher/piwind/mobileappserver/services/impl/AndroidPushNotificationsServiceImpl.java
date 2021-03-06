package com.piotrmajcher.piwind.mobileappserver.services.impl;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.piotrmajcher.piwind.mobileappserver.domain.NotificationsRequest;
import com.piotrmajcher.piwind.mobileappserver.services.AndroidPushNotificationsService;
import com.piotrmajcher.piwind.mobileappserver.services.MeteoStationService;
import com.piotrmajcher.piwind.mobileappserver.services.exceptions.MeteoStationServiceException;
import com.piotrmajcher.piwind.mobileappserver.web.dto.MeteoDataTOAndroid;
import com.piotrmajcher.piwind.mobileappserver.web.dto.MeteoStationTO;
import com.piotrmajcher.piwind.mobileappserver.web.rest.HeaderRequestInterceptor;

@Service
public class AndroidPushNotificationsServiceImpl implements AndroidPushNotificationsService {
	private static final Logger logger = Logger.getLogger(AndroidPushNotificationsServiceImpl.class);
	private static final String FIREBASE_SERVER_KEY = "AAAAskASe5Q:APA91bHI37wmTGb063qT80i3RZ35tIpVKjQq7D-OxhL_EApX-WoL-K_Vg2XO8RPO-TzLSrG7ZYl-OgMhYThaaXcJXcrpxa71rkrInp7V0H4sXodz1DPcp4hK13nprKL3jp1OH8WbLr9B";
	private static final String FIREBASE_API_URL = "https://fcm.googleapis.com/fcm/send";
	
	private static final String WIND_PICKED_UP_MESSAGE = "The wind has picked!";
	private static final String WIND_DROPPED_MESSAGE = "The wind has dropped ...";
	
	private RestTemplate restTemplate;
	private MeteoStationService meteoStationService;
	private Set<NotificationsRequest> notificationsSentSet;
	
	@Autowired
	public AndroidPushNotificationsServiceImpl(MeteoStationService meteoStationService) {
		this.restTemplate = new RestTemplate();
		this.meteoStationService = meteoStationService;
		this.notificationsSentSet = new HashSet<>();
	}
	
	@Async
	@Override
	public CompletableFuture<String> send(HttpEntity<String> entity) {
		
		/**
		https://fcm.googleapis.com/fcm/send
		Content-Type:application/json
		Authorization:key=FIREBASE_SERVER_KEY*/
		
		List<ClientHttpRequestInterceptor> interceptors = new LinkedList<>();
		interceptors.add(new HeaderRequestInterceptor("Authorization", "key=" + FIREBASE_SERVER_KEY));
		interceptors.add(new HeaderRequestInterceptor("Content-Type", "application/json"));
		restTemplate.setInterceptors(interceptors);
 
		String firebaseResponse = restTemplate.postForObject(FIREBASE_API_URL, entity, String.class);
 
		return CompletableFuture.completedFuture(firebaseResponse);
	}

	@Override
	public void handleMeteoDataUpdate(UUID stationId, MeteoDataTOAndroid updatedMeteoData) {
		
		List<NotificationsRequest> notificationsRequestsForStation = meteoStationService.findAllNotificationsRequestsForStation(stationId);
		
		for (NotificationsRequest notificationsRequest : notificationsRequestsForStation) {
			try {
				handleNotificationsRequest(stationId, updatedMeteoData, notificationsRequest);
			} catch (ExecutionException | InterruptedException | JSONException | MeteoStationServiceException e) {
				logger.error("Error while trying to send the notification: " + e.getMessage());
			}
		}
	}

	private void handleNotificationsRequest(UUID stationId, MeteoDataTOAndroid updatedMeteoData,
			NotificationsRequest notificationsRequest)
			throws MeteoStationServiceException, InterruptedException, ExecutionException {
		if (updatedMeteoData.getWindSpeed() >= notificationsRequest.getMinWindLimit()) {
			if (!notificationsSentSet.contains(notificationsRequest)) {
				sendNotificationToStation(stationId, updatedMeteoData, notificationsRequest, WIND_PICKED_UP_MESSAGE);
				notificationsSentSet.add(notificationsRequest);
			}
		} else {
			if (notificationsSentSet.contains(notificationsRequest)) {
				sendNotificationToStation(stationId, updatedMeteoData, notificationsRequest, WIND_DROPPED_MESSAGE);
				notificationsSentSet.remove(notificationsRequest);
			}
		}
	}


	private void sendNotificationToStation(
			UUID stationId,
			MeteoDataTOAndroid updatedMeteoData,
			NotificationsRequest notificationsRequest,
			String notificationMsg)
			throws MeteoStationServiceException, InterruptedException, ExecutionException {
		MeteoStationTO station = meteoStationService.getStation(stationId);
		JSONObject body = createNotificationBody(station, notificationsRequest.getUsername(), updatedMeteoData, notificationMsg);
		
		HttpEntity<String> request = new HttpEntity<>(body.toString());
		CompletableFuture<String> pushNotification = send(request);
		CompletableFuture.allOf(pushNotification).join();
		String firebaseResponse = pushNotification.get();
		logger.info("Notification for station with id " + stationId + " has been sent!");
		logger.info("Firebase response: " + firebaseResponse);
	}
	
	private JSONObject createNotificationBody(
			MeteoStationTO station, 
			String username, 
			MeteoDataTOAndroid updatedMeteoData, 
			String notificationMsg) throws JSONException {
		/**
		{
		   "notification": {
		      "title": "<station_name>",
		      "body": "The wind has picked up!"
		   },
		   "data": {
		      "station_id": "<station_uuid>",
		      "name": "<station_name>",
		      "stationBaseURL": "<station_url> 
		   },
		   "to": "/topics/<station_uuid><user_uuid>",
		   "priority": "high"
		}
		 */
		
		JSONObject body = new JSONObject();
		
		body.put("to", buildTopicUrl(station.getId().toString(), username));
		body.put("priority", "high");
 
		JSONObject notification = new JSONObject();
		notification.put("title", station.getName());
		notification.put("body", notificationMsg);
		notification.put("sound", "default");
		
		JSONObject data = new JSONObject();
		data.put("id", station.getId().toString());
		data.put("name", station.getName());
		data.put("stationBaseURL", station.getStationBaseURL());
 
		body.put("notification", notification);
		body.put("data", data);
		
		return body;
	}
	
	private String buildTopicUrl(String stationId, String username) {
		StringBuilder sb = new StringBuilder();
		sb.append("/topics/");
		sb.append(stationId);
		sb.append(username);
		
		return sb.toString();
	}

}
