package com.github.cecchisandrone.smarthome.service.raspsonar;

import static com.googlecode.charts4j.Color.WHITE;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.LinkedList;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.github.cecchisandrone.smarthome.domain.Configuration;
import com.github.cecchisandrone.smarthome.domain.RaspsonarConfiguration;
import com.github.cecchisandrone.smarthome.service.configuration.ConfigurationService;
import com.github.cecchisandrone.smarthome.service.configuration.ConfigurationServiceException;
import com.github.cecchisandrone.smarthome.service.notification.NotificationService;
import com.github.cecchisandrone.smarthome.service.notification.NotificationServiceException;
import com.googlecode.charts4j.AxisLabels;
import com.googlecode.charts4j.AxisLabelsFactory;
import com.googlecode.charts4j.AxisStyle;
import com.googlecode.charts4j.AxisTextAlignment;
import com.googlecode.charts4j.Color;
import com.googlecode.charts4j.Data;
import com.googlecode.charts4j.Fills;
import com.googlecode.charts4j.GCharts;
import com.googlecode.charts4j.Line;
import com.googlecode.charts4j.LineChart;
import com.googlecode.charts4j.LineStyle;
import com.googlecode.charts4j.LinearGradientFill;
import com.googlecode.charts4j.Plots;
import com.googlecode.charts4j.Shape;

@Component
public class RaspsonarServiceImpl implements InitializingBean, RaspsonarService {

	private static final int MEASUREMENTS = 5;

	private static final int DISTANCE_VALUES_TO_HOLD = 100;

	private static final Logger logger = LoggerFactory.getLogger(RaspsonarServiceImpl.class);

	private static final double WRONG_MEASUREMENT_THRESHOLD = 15;

	private double averageDistance;

	private WebTarget sonarServiceTarget;

	private boolean relayStatus;

	private LinkedList<Double> distanceMeasurements = new LinkedList<Double>();

	private RaspsonarConfiguration configuration;

	@Autowired
	private ConfigurationService configurationService;

	@Autowired
	private NotificationService notificationService;

	@Override
	public void afterPropertiesSet() throws Exception {

		try {
			loadClientConfiguration();
		} catch (Exception e) {
			logger.error("Unable to initialize Raspsonar configuration", e);
		}
	}

	private void loadClientConfiguration() throws ConfigurationServiceException {

		// Instantiate service client
		Client client = ClientBuilder.newClient();
		configuration = configurationService.getConfiguration().getRaspsonarConfiguration();
		if (configuration != null) {
			sonarServiceTarget = client.target(configuration.getServiceUrl());
		} else {
			throw new ConfigurationServiceException("Configuration object is null!");
		}
	}

	@Override
	public synchronized Double getDistance(boolean resetAverageDistance) throws RaspsonarServiceException {

		if (!resetAverageDistance && averageDistance != 0) {
			return averageDistance;
		} else {
			try {
				loadClientConfiguration();
			} catch (ConfigurationServiceException e) {
				throw new RaspsonarServiceException("Can't load Raspsonar configuration. Reason: " + e.toString());
			}

			WebTarget distanceTarget = sonarServiceTarget.path("sonar").path(configuration.getSonarIndex().toString())
					.path("distance").queryParam("measurements", MEASUREMENTS);
			Builder request = distanceTarget.request();
			Response response;
			try {
				response = request.get();
				if (response.getStatus() == HttpURLConnection.HTTP_OK) {
					String distanceAsString = response.readEntity(String.class);
					Double distance = new Double(distanceAsString);

					// Check wrong measurements, maybe due to CPU allocation
					if (averageDistance != 0 && (distance - averageDistance) > WRONG_MEASUREMENT_THRESHOLD) {

						logger.warn("Wrong measurement detected. Previous distance value: " + averageDistance
								+ ". Actual: " + distance);

						// Do another measurement
						getDistance(resetAverageDistance);
					}

					if (averageDistance == 0 || resetAverageDistance) {
						averageDistance = distance;
					}
					averageDistance = (distance + averageDistance) / 2;

					// Update distance measurements for chart
					if (distanceMeasurements.size() > DISTANCE_VALUES_TO_HOLD) {
						distanceMeasurements.removeFirst();
					}
					distanceMeasurements.add(averageDistance);

					return new BigDecimal(averageDistance).setScale(1, RoundingMode.CEILING).doubleValue();
				}
				throw new RaspsonarServiceException(
						"Can't access remote service. Response code: " + response.getStatus());
			} catch (Exception e) {
				throw new RaspsonarServiceException("Can't access remote service. Reason: " + e.toString(), e);
			}
		}
	}

	// Every 2 hours
	@Override
	@Scheduled(cron = "0 0 0/2 * * ?")
	public void checkDistanceTask() {

		try {
			logger.info("Checking distance threshold...");
			Double distance = getDistance(true);
			logger.info("Distance is " + distance);
			Configuration configuration = configurationService.getConfiguration();
			if (distance < configuration.getRaspsonarConfiguration().getDistanceThreshold()) {
				logger.info("Alerting user");
				notificationService.sendSlackNotification(configuration.getSlackConfiguration(),
						"Warning! Distance threshold has been trespassed. Value: " + distance);
			}

		} catch (ConfigurationServiceException | RaspsonarServiceException | NotificationServiceException e) {
			logger.error(e.toString(), e);
		}
	}

	@Override
	public void toggleRelay(boolean status) throws RaspsonarServiceException {

		try {
			loadClientConfiguration();
		} catch (ConfigurationServiceException e) {
			throw new RaspsonarServiceException("Can't load Raspsonar configuration. Reason: " + e.toString());
		}

		logger.info("Toggling relay to status: " + status);

		WebTarget toggleRelayTarget = sonarServiceTarget.path("relay").path(configuration.getRelayIndex().toString())
				.path("toggleRelay").queryParam("status", status);
		Builder request = toggleRelayTarget.request();
		Response response = request.post(null);
		if (response.getStatus() != HttpURLConnection.HTTP_OK) {
			throw new RaspsonarServiceException("Can't access remote service. Response code: " + response.getStatus());
		}

		// Update relay status
		relayStatus = status;
	}

	@Override
	@Scheduled(cron = "*/30 * * * * ?")
	public void autoPowerOffRelay() {

		if (relayStatus) {
			try {
				Double distance = getDistance(false);
				logger.info("Checking if relay should be put off. Distance: " + distance);
				Configuration configuration = configurationService.getConfiguration();
				// Toggle relay off is threshold is trespassed
				if (distance > configuration.getRaspsonarConfiguration().getAutoPowerOffDistanceThreshold()) {
					logger.info("Toggling relay off...threshold is trespassed");
					toggleRelay(false);
					notificationService.sendSlackNotification(configuration.getSlackConfiguration(),
							"Auto power off distance threshold ("
									+ configuration.getRaspsonarConfiguration().getAutoPowerOffDistanceThreshold()
									+ " cm) trespassed. Powering off the pump");
				}
			} catch (ConfigurationServiceException | RaspsonarServiceException | NotificationServiceException e) {
				logger.error(e.toString(), e);
			}
		}
	}

	@Override
	public boolean isRelayStatus() {
		return relayStatus;
	}

	@Override
	public String getDistanceChartUrl() {

		Line line1 = Plots.newLine(Data.newData(distanceMeasurements), Color.newColor("65b459"), "Distance");
		line1.setLineStyle(LineStyle.newLineStyle(2, 1, 0));
		line1.addShapeMarkers(Shape.DIAMOND, Color.WHITE, 3);

		// Defining chart
		LineChart chart = GCharts.newLineChart(line1);
		chart.setSize(450, 350);
		chart.setTitle("Distance measurements (Last " + DISTANCE_VALUES_TO_HOLD + " values)", WHITE, 14);
		chart.setGrid(25, 25, 3, 2);

		// Defining axis info and styles
		AxisStyle axisStyle = AxisStyle.newAxisStyle(WHITE, 12, AxisTextAlignment.CENTER);

		AxisLabels yAxis = AxisLabelsFactory.newNumericAxisLabels(Collections.min(distanceMeasurements),
				Collections.max(distanceMeasurements));
		AxisLabels xAxis = AxisLabelsFactory.newAxisLabels("Time", 50.0);
		AxisLabels yAxis2 = AxisLabelsFactory.newAxisLabels("Distance", 50.0);

		xAxis.setAxisStyle(axisStyle);
		yAxis2.setAxisStyle(axisStyle);
		yAxis.setAxisStyle(axisStyle);

		// Adding axis info to chart
		chart.addXAxisLabels(xAxis);
		chart.addYAxisLabels(yAxis);
		chart.addYAxisLabels(yAxis2);

		// Defining background and chart fills.
		chart.setBackgroundFill(Fills.newSolidFill(Color.newColor("777777")));
		LinearGradientFill fill = Fills.newLinearGradientFill(0, Color.newColor("363433"), 100);
		fill.addColorAndOffset(Color.newColor("2E2B2A"), 0);
		chart.setAreaFill(fill);
		return chart.toURLString();
	}
}
