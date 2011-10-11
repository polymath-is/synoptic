package synopticgwt.server;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;

/**
 * Servlet that handles POST form submission containing log file and saving
 * that file onto server disk. Saved to disk for following log file parsing
 * in server side. The request must be a multi-part response to handle
 * form inputs.
 */
public class LogFileUploadServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public static Logger logger = Logger.getLogger("LogFileUploadServlet");
	 
	 // The directory in which files are currently exported to.
    private static final String userLogFileExport = "userlogfileexport/";
	
	/**
	 * Handles POST request. Retrieves file uploaded in form from request and saves
	 * file on disk. Response sends error if request is not multi-part.
	 * @param request a multi-part request
	 * @param response
 	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/plain");
		
		// Process only multipart requests.
		if (ServletFileUpload.isMultipartContent(request)) {
			FileItem uploadedItem = null;
			try {
				uploadedItem = getFileItem(request, response);
			} catch (FileUploadException e1) {
				logger.severe("FileUploadException while parsing request : " + e1.getMessage());
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing uploaded file");
				return;
			}
			if (uploadedItem == null) {
				logger.severe("No form was submitted");
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"No form was submitted");
				return;
			}
			if (uploadedItem.getSize() == 0) {
				logger.severe("No file was uploaded");
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"No file was uploaded");
				return;
			}
			String fileName = uploadedItem.getName();
			String path = userLogFileExport + fileName;
			if (fileName != null && !fileName.equals("")) {
				fileName = FilenameUtils.getName(fileName);
				File uploadedFile = new File(path);
				try {
					uploadedItem.write(uploadedFile);
				} catch (Exception e) {
					logger.severe("Unable to save file to server : " + e.getMessage());
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR , "Unable to save file to server");
				} 
			}
			
			// Successfully uploaded file to server's disk
   			response.setStatus(HttpServletResponse.SC_CREATED);
   			response.getWriter().write("Successfully uploaded file");
   			
   			// Saves file path of file to session state
   			HttpSession session = request.getSession();
   			session.setAttribute("logFilePath", path);
		} else {
			logger.severe("Not multipart request");
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not multipart request");
			return;
		}
	}
	
	// Returns uploaded file from given request.
	@SuppressWarnings("unchecked")
	private FileItem getFileItem(HttpServletRequest request, HttpServletResponse response) throws FileUploadException  {
		// Create a factory for disk-based file items
		FileItemFactory factory = new DiskFileItemFactory();
	
		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		try {
			List<FileItem> items = upload.parseRequest(request);
			Iterator<FileItem> iter = items.iterator();
			while (iter.hasNext()) {
				FileItem item = (FileItem) iter.next();
				
				// FileItem is uploaded file and not simple form field
				if (!item.isFormField()) {
					return item;
				}
			}
		} catch (FileUploadException e) {
			throw new FileUploadException("FileUploadException while parsing request");
		}
		return null;
	}
}
