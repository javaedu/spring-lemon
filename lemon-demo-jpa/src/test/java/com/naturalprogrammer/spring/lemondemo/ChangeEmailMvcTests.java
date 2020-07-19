package com.naturalprogrammer.spring.lemondemo;

import com.naturalprogrammer.spring.lemon.commons.security.GreenTokenService;
import com.naturalprogrammer.spring.lemon.commons.util.LecUtils;
import com.naturalprogrammer.spring.lemondemo.entities.User;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChangeEmailMvcTests extends AbstractMvcTests {
	
	private static final String NEW_EMAIL = "new.email@example.com";

	private String changeEmailCode;
	
	@Autowired
	private GreenTokenService greenTokenService;
	
	@BeforeEach
	public void setUp() {
		
		User user = userRepository.findById(UNVERIFIED_USER_ID).get();
		user.setNewEmail(NEW_EMAIL);
		userRepository.save(user);
		
		changeEmailCode = greenTokenService.createToken(
				GreenTokenService.CHANGE_EMAIL_AUDIENCE,
				Long.toString(UNVERIFIED_USER_ID), 60000L,
				LecUtils.mapOf("newEmail", NEW_EMAIL));
	}

	@Test
	void testChangeEmail() throws Exception {
		
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", changeEmailCode)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(200))
				.andExpect(header().string(LecUtils.TOKEN_RESPONSE_HEADER_NAME, containsString(".")))
				.andExpect(jsonPath("$.id").value(UNVERIFIED_USER_ID));
		
		User updatedUser = userRepository.findById(UNVERIFIED_USER_ID).get();
		assertNull(updatedUser.getNewEmail());
		assertEquals(NEW_EMAIL, updatedUser.getEmail());
		
		// Shouldn't be able to login with old token
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", changeEmailCode)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(401));
	}
	
    /**
     * Providing a wrong changeEmailCode shouldn't work.
     */
	@Test
	void testChangeEmailWrongCode() throws Exception {
		
		// Blank token
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", "")
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(422));

		// Wrong audience
		String code = greenTokenService.createToken(
				"", // blank audience
				Long.toString(UNVERIFIED_USER_ID), 60000L,
				LecUtils.mapOf("newEmail", NEW_EMAIL));
		
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", code)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(401));

		// Wrong userId subject
		code = greenTokenService.createToken(
				GreenTokenService.CHANGE_EMAIL_AUDIENCE,
				Long.toString(ADMIN_ID), 60000L,
				LecUtils.mapOf("newEmail", NEW_EMAIL));
		
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", code)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(403));
		
		// Wrong new email
		code = greenTokenService.createToken(
				GreenTokenService.CHANGE_EMAIL_AUDIENCE,
				Long.toString(UNVERIFIED_USER_ID), 60000L,
				LecUtils.mapOf("newEmail", "wrong.new.email@example.com"));
		
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", code)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(403));
	}
	
    /**
     * Providing an obsolete changeEmailCode shouldn't work.
     */
	@Test
	void testChangeEmailObsoleteCode() throws Exception {

		// credentials updated after the request for email change was made
		User user = userRepository.findById(UNVERIFIED_USER_ID).get();
		user.setCredentialsUpdatedMillis(System.currentTimeMillis() + 1);
		userRepository.save(user);
		
		// A new auth token is needed, because old one would be obsolete!
		String authToken = login(UNVERIFIED_USER_EMAIL, USER_PASSWORD);
		
		// now ready to test!
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", changeEmailCode)
				.header(HttpHeaders.AUTHORIZATION, authToken)
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(401));	
	}
	
	/**
     * Trying without having requested first.
     */
	@Test
	void testChangeEmailWithoutAnyRequest() throws Exception {

		mvc.perform(post("/api/core/users/{id}/email", USER_ID)
                .param("code", changeEmailCode)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(422));
	}
	
    /**
     * Trying after some user registers the newEmail, leaving it non unique.
     */
	@Test
	void testChangeEmailNonUniqueEmail() throws Exception {
		
		// Some other user changed to the same email
		User user = userRepository.findById(ADMIN_ID).get();
		user.setEmail(NEW_EMAIL);
		userRepository.save(user);
		
		mvc.perform(post("/api/core/users/{id}/email", UNVERIFIED_USER_ID)
                .param("code", changeEmailCode)
				.header(HttpHeaders.AUTHORIZATION, tokens.get(UNVERIFIED_USER_ID))
                .header("contentType",  MediaType.APPLICATION_FORM_URLENCODED))
		        .andExpect(status().is(422));
	}
}
