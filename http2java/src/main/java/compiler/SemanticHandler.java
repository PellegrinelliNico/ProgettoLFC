package compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.antlr.runtime.Token;

import variables.*;


public class SemanticHandler {
	//errors and warnings handling
	private static final int ALREADY_DEF_HDR_ERR = 10;
	private static final int NO_HOST_ERR = 11;
	private static final int CHARSET_ERR = 12;
	private static final int BOUNDARY_ERR = 13;
	private static final int BASIC_ERR = 14;
	private static final int DIGEST_ERR = 15;
	private static final int DIGEST_ELEMENT_ERR = 16;
	private static final int ALREADY_DEF_DIGEST_ELEMENT_ERR = 17;
	private static final int LANGUAGE_ERR = 18;
	private static final int ENCODING_ELEMENT_ERR = 19;
	
	private static final int BODY_GET_WARN = 100;
	private static final int NO_BODY_POST_WARN = 101;
	private static final int NO_CT_POST_WARN = 102;
	private static final int CT_GET_WARN = 103;
	private List<CompilerError> errors;
	private List<CompilerError> warnings;
	private List<String> digestElements;
	//generated by request line
	private RequestLine requestLine;
	//generated by headers
	private List<Header> headers;
	//generated by body
	private String body;
	private boolean existsBody;
	//final output
	private String javaCode;

	//constructor
	public SemanticHandler () {
		errors = new ArrayList<CompilerError>();
		warnings = new ArrayList<CompilerError>();
		digestElements = new ArrayList<String>();
		
		headers = new ArrayList<Header>();
		body = "";
		existsBody = false;
		
		javaCode = "";
	}
	
	public List<CompilerError> getErrors() {
		return errors;
	}
	public List<CompilerError> getWarnings() {
		return warnings;
	}
	public String getJavaCode() {
		return javaCode;
	}
	
	public RequestLine createRequestLine(String m, String p, String v) {	
		return new RequestLine(m, p, v);
	}

	public void addRequestLine(RequestLine rl) {
		requestLine = rl;
	}

	public void addHeader(Header hdr) {
		if(hdr == null) {
			return;
		}
		String hdr_key =  hdr.getKey().getText().replaceAll("'", "");;
		for(Header h: headers) {
			String h_key = h.getKey().getText().replaceAll("'", "");
			if(hdr_key.equals(h_key)) {
				addError(hdr.getKey(), ALREADY_DEF_HDR_ERR);
				return;
			}
		}					
		headers.add(hdr);
	}
	
	public void checkHeaders() {
		boolean foundHost = false;
		boolean foundCT = false;
		for(Header h: headers) {
			String h_key = h.getKey().getText().replaceAll("'", "");
			if(h_key.equals("Host"))
				foundHost = true;
			if(h_key.equals("Content-Type"))
				foundCT = true;
		}
		if(!foundHost)
			addError(NO_HOST_ERR);
		if(requestLine != null && requestLine.getMethod() != null && requestLine.getMethod().equals("POST") && !foundCT)
			addWarning(NO_CT_POST_WARN);
		if(requestLine != null && requestLine.getMethod() != null && requestLine.getMethod().equals("GET") && foundCT)
			addWarning(CT_GET_WARN);
	}

	public String handleQuotes(String text) {
		return text.replaceAll("\"", "\\\\\"").replaceAll("'", "");
	}
	
	public String handleDigestElement(String de, String str) {
		List<String> des_without_dq = new ArrayList<String>(
				Arrays.asList("algorithm", "nc", "qop"));
		if(des_without_dq.contains(de)) {
			return str.replaceAll("\"", "\\\\\"").replaceAll("'", "");
		}
		return str.replaceAll("'", "\"").replaceAll("\"", "\\\\\"");
	}
	
	public String handleBody(String text) {
		return text.replaceAll("\"", "\\\\\"").replaceAll("°", "");
	}
	
	public void addBody(String b) {
		body = b;
		existsBody = true;
	}
	
	public void generateJavaCode() {
		if(requestLine != null && requestLine.getMethod() != null && requestLine.getMethod().equals("POST") && !existsBody)
			addWarning(NO_BODY_POST_WARN);
		if(requestLine != null && requestLine.getMethod() != null && requestLine.getMethod().equals("GET") && existsBody)
			addWarning(BODY_GET_WARN);
		
		if(!errors.isEmpty())
			return;
		
		//first line
		javaCode =  "HttpRequest request = HttpRequest.newBuilder()\n";
		
		//http version
		if(requestLine.getVersion().equals("HTTP/1.1"))
			javaCode += "\t.version(HttpClient.Version.HTTP_1_1)\n";
		else
			javaCode += "\t.version(HttpClient.Version.HTTP_2)\n";
		
		//headers & uri
		String headersStr = "";
		for(Header h: headers) {
			String key = h.getKey().getText().replaceAll("'", "");
			if(key.equals("Host")) {
				javaCode += "\t.uri(new URI(\"http://" + h.getValue() + requestLine.getPath() + "\"))\n";
			} else {
				headersStr += "\t.header(\"" + key +"\", \"" + h.getValue() + "\")\n";
			}
		}
		javaCode += headersStr;
		
		//method & body
		if(requestLine.getMethod().equals("GET")) {
			javaCode += "\t.GET()\n";
		} else {
			if(existsBody)
				javaCode += "\t.POST(HttpRequest.BodyPublishers.ofString(\"" + body + "\"))\n";
			else
				javaCode += "\t.POST(HttpRequest.BodyPublishers.noBody())\n";
		}
		
		//build
		javaCode += "\t.build();";
	}

	public void handleError(Token tk, String hdr, String msg) {
		String coord = "[" + tk.getLine() + ":" + (tk.getCharPositionInLine()+1) + "]";
		if(tk.getType() == HttpLexer.ERROR_TOKEN) {
			errors.add(new CompilerError(tk.getLine(), (tk.getCharPositionInLine()+1), coord + " Lexical error:\t" + msg));
		} else {
			errors.add(new CompilerError(tk.getLine(), (tk.getCharPositionInLine()+1), coord + " Syntax error:\t" + msg));
		}
	}
	
	public void addError(int errorCode) {
		String err = "Semantic error:\t";
		switch (errorCode) {
		case NO_HOST_ERR:
			err += "Host header never defined";
			break;
		}
		errors.add(new CompilerError(-1, -1, err));
	}
	
	public void addError(Token tk, int errorCode) {
		String err = "[" + tk.getLine() + ":" + (tk.getCharPositionInLine()+1) + "]";
		err += " Semantic error:\t";
		switch (errorCode) {
		case ALREADY_DEF_HDR_ERR:
			err += tk.getText() + " header is already defined";
			break;
		case CHARSET_ERR:
			err += "expected 'charset' but found '" + tk.getText() + "'";
			break;
		case BOUNDARY_ERR:
			err += "expected 'boundary' but found '" + tk.getText() + "'";
			break;
		case BASIC_ERR:
			err += "expected 'Basic' but found '" + tk.getText() + "'";
			break;
		case DIGEST_ERR:
			err += "expected 'Digest' but found '" + tk.getText() + "'";
			break;
		case DIGEST_ELEMENT_ERR:
			err += "'" + tk.getText() + "' is not a valid digest parameter";
			break;
		case ALREADY_DEF_DIGEST_ELEMENT_ERR:
			err += tk.getText() + " digest parameter is already defined";
			break;
		case LANGUAGE_ERR:
			err += "base language tag '" + tk.getText() + "' is incorrect, it must have 2 or 3 characters";
			break;
		case ENCODING_ELEMENT_ERR:
			err += "'" + tk.getText() + "' is not a valid encoding element";
			break;
		}
		errors.add(new CompilerError(tk.getLine(), (tk.getCharPositionInLine()+1), err));
	}
	
	public void addWarning(int warningCode) {
		String err = "Warning:\t";
		switch (warningCode) {
		case BODY_GET_WARN:
			err += "GET requests should not have a body";
			break;
		case NO_BODY_POST_WARN:
			err += "POST requests should have a body";
			break;
		case NO_CT_POST_WARN:
			err += "POST requests should have the Content-Type header";
			break;
		case CT_GET_WARN:
			err += "GET requests should not have the Content-Type header";
			break;
		}
		warnings.add(new CompilerError(-1, -1, err));
	}

	public void checkCharset(Token tk) {
		if(!tk.getText().equals("charset"))
			addError(tk, CHARSET_ERR);
	}

	public void checkBoundary(Token tk) {
		if(!tk.getText().equals("boundary"))
			addError(tk, BOUNDARY_ERR);
	}

	public void checkLanguage(Token tk) {
		if(tk.getText().length() < 2 || tk.getText().length() > 3)
			addError(tk, LANGUAGE_ERR);
	}

	public void checkEncodingElement(Token tk) {
		List<String> ees = new ArrayList<String>(Arrays.asList("gzip", "compress", "deflate", "br", "identity"));
		if(!ees.contains(tk.getText()))
			addError(tk, ENCODING_ELEMENT_ERR);
	}
	
	public String checkBasic(Token tk) {
		if(!tk.getText().equals("Basic"))
			addError(tk, BASIC_ERR);
		return tk.getText();
	}

	public String checkDigest(Token tk) {
		if(!tk.getText().equals("Digest"))
			addError(tk, DIGEST_ERR);
		return tk.getText();
	}

	public void checkDigestElement(Token tk) {
		List<String> des = new ArrayList<String>(
				Arrays.asList("username", "realm", "uri", "algorithm", "nonce", "nc", "cnonce", "qop", "response", "opaque"));
		if(!des.contains(tk.getText())) {
			addError(tk, DIGEST_ELEMENT_ERR);
			return;
		}
		for(String d: digestElements) {
			if(d.equals(tk.getText())) {
				addError(tk, ALREADY_DEF_DIGEST_ELEMENT_ERR);
				return;
			}
		}
		digestElements.add(tk.getText());
	}
}
