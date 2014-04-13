package it.cecchi.smarthome.web;

import it.cecchi.smarthome.service.raspsonar.RaspsonarService;
import it.cecchi.smarthome.service.raspsonar.RaspsonarServiceException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles requests for the application home page.
 */
@Controller
public class DashboardController {

	@Autowired
	private RaspsonarService sonarService;

	@RequestMapping(value = {"/dashboard", "/"}, method = RequestMethod.GET)
	public ModelAndView home() {

		ModelAndView modelAndView = new ModelAndView(ViewNames.DASHBOARD);
		try {
			modelAndView.addObject("waterLevel", sonarService.getDistance());
		} catch (RaspsonarServiceException e) {
			modelAndView.addObject("waterLevelError", e.toString());
		}
		return modelAndView;
	}

	@RequestMapping("/error")
	public String error(HttpServletRequest request, Model model) {

		model.addAttribute("errorCode", request.getAttribute("javax.servlet.error.status_code"));
		Throwable throwable = (Throwable) request.getAttribute("javax.servlet.error.exception");
		String errorMessage = null;
		if (throwable != null) {
			errorMessage = throwable.toString();
		}
		model.addAttribute("errorMessage", errorMessage);
		return ViewNames.ERROR;
	}
}
