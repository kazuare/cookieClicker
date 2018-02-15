<%@ page import="yakushkin.Utils" %>
<% 
if(request.getParameter( "username" ) == null)
	response.sendRedirect("login.jsp"); 
%>
<!DOCTYPE html>
<html>
<head>
<script src='http://code.jquery.com/jquery-latest.min.js' type='text/javascript'></script>
<script>
function cookieClicked(){
	console.log('cookie is clicked!');
	$.get( "Gate", 
			{ 
			intention: "addEvent", 			
			event: "cookie", 			
			username:"<%= Utils.escapeUsername("" + request.getParameter("username")) %>" 
			}
		);
}
function factoryClicked(){
	console.log('factory is clicked!');
	$.get( "Gate", 
			{ 
			intention: "addEvent", 			
			event: "factory", 			
			username:"<%= Utils.escapeUsername("" + request.getParameter("username")) %>" 
			}
		);
}
function sec() {
	$.get( "Gate", 
		{ 
		intention: "getUpdates", 			
		username:"<%= Utils.escapeUsername("" + request.getParameter("username")) %>" 
		},
		function( data ) {
			$( "#cookieCount" ).html( data.substring(0, data.indexOf(';')));
			$( "#factoryCount" ).html( data.substring(data.indexOf(';')+1, data.indexOf(';;')));
			$( "#leaderboard" ).html( data.substring(data.indexOf(';;')+2));
		}
	);
}
setInterval(sec, 500) 
</script>
</head>
<body>
<style>div{display: inline-flex;}</style>
<p>Hello, <%= Utils.escapeUsername("" + request.getParameter("username")) %> </p>
<div>
	<p>LEADERBOARD:</p>
	<br/>
	<p id="leaderboard">LEADERBOARD:</p>
</div>
<div><img src='./Cookie.gif' onclick='cookieClicked()'></div>
<div>
	<img src='./factory.jpg' onclick='factoryClicked()'>
	<p>You own <span id="cookieCount">X</span> cookies</p>
	<br/>
	<p>You own <span id="factoryCount">X</span> factories</p>
</div>
</body>
</html>