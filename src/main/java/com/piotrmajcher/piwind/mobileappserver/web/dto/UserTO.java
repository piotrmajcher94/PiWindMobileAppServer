package com.piotrmajcher.piwind.mobileappserver.web.dto;

public class UserTO {
	
	private String username;
	private String email;
	private String password;
	private String matchingPassword;
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getMatchingPassword() {
		return matchingPassword;
	}
	public void setMatchingPassword(String matchingPassword) {
		this.matchingPassword = matchingPassword;
	}
	
	@Override
	public String toString() {
		return "RegisterUserTO [username=" + username + ", email=" + email + "]";
	}
}
