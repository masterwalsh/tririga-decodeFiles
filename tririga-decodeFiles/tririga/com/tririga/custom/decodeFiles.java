package com.tririga.custom;


import java.io.InputStream;
import java.util.UUID;

import org.apache.axiom.attachments.ByteArrayDataSource;
import org.apache.log4j.Logger;
import com.tririga.pub.workflow.CustomBusinessConnectTask;
import com.tririga.pub.workflow.Record;
import com.tririga.ws.TririgaWS;
import com.tririga.ws.dto.IntegrationField;
import com.tririga.ws.dto.IntegrationRecord;
import com.tririga.ws.dto.IntegrationSection;
import com.tririga.ws.dto.ResponseHelper;
import com.tririga.ws.dto.ResponseHelperHeader;
import com.tririga.ws.dto.content.Content;
import com.tririga.ws.dto.content.Response;
import com.tririga.ws.errors.ModuleDoesNotExistException;
import com.tririga.ws.errors.ObjectTypeDoesNotExistException;
import javax.activation.DataHandler;
import org.apache.commons.io.IOUtils;
import java.util.Base64;

public class decodeFiles implements CustomBusinessConnectTask {
    private Long startms = 0L;
    long specId = 0L;
    public static String fileSize = null;
    public static String fileName = null;
    private static final Logger log = Logger.getLogger(decodeFiles.class);

    public decodeFiles() {
    }

    public boolean execute(TririgaWS tws, long userId, Record[] recs) {
        this.specId = recs[0].getRecordId();
        this.startms = System.currentTimeMillis();
        this.register(userId, tws);
        try {
	            
        	//Prepare the inputstream with the document content download
        	InputStream inStream = docDownload(tws, this.specId,(String)null);
		    if (inStream == null) {
		    	log.error("No content in Document");
		        return this.wfExit(this.startms, false);
		        }
            //get file name to return decoded file
            getFileDetails(tws, this.specId);
            //Place encoded InputStream into byte array encBytes       
            byte[] encBytes = IOUtils.toByteArray(inStream);
            // prepare decoder & decode into decBytes
            Base64.Decoder dec = Base64.getDecoder();
            byte[] decBytes = dec.decode(encBytes);
            // prepare decoded bytes into Data Source, DataHandler and finally into a Content Object
            ByteArrayDataSource ds = new ByteArrayDataSource(decBytes, fileName);
            DataHandler dh = new DataHandler(ds);
            Content c = new Content(this.specId, dh, (String)null, fileName, (String)null);
            // Upload content object through web services
            tws.upload(c);
            inStream.close();          
	            
        } catch (Exception var10) {
            log.error("Unable to copy content from Binary field to Document! " + var10);
        }

        return this.wfExit(this.startms, true);
    }

    private boolean register(Long userId, TririgaWS tws) {
        try {
            tws.register(userId);
            return true;
        } catch (Exception var4) {
            log.error("Could not register TririgaWS Soap client.", var4);
            return this.wfExit(this.startms, false);
        }
    }

    private boolean wfExit(long startms, boolean retVal) {
        long stopms = System.currentTimeMillis();
        long executionTimeMS = stopms - startms;
        log.debug("finished - execution time: " + executionTimeMS + "ms");
        return retVal;
    }

    public static void updateRecord(TririgaWS tws, String value, long recordid) {
        String section_name = "General";
        int module_id = 0;

        try {
            module_id = tws.getModuleId("Document");
        } catch (ModuleDoesNotExistException var21) {
            log.error("Module name lookup failed for module name [Document] - module not found");
        } catch (Exception var22) {
            log.error("Module name lookup failed for module name [Document] - " + var22.toString());
        }

        long bo_id = 0L;
        long gui_id = 0L;

        try {
            bo_id = tws.getObjectTypeId("Document", "Document");
            gui_id = tws.getDefaultGuiId(bo_id);
        } catch (ObjectTypeDoesNotExistException var19) {
            log.error("BO name lookup failed for module/bo name [Document] / [Document] - BO not found");
        } catch (Exception var20) {
            log.error("BO name lookup failed for module/bo name [Document] / [Document] - " + var20.toString());
        }

        try {
            IntegrationRecord ir = new IntegrationRecord();
            ir.setId(recordid);
            ir.setModuleId(module_id);
            ir.setObjectTypeId(bo_id);
            ir.setObjectTypeName("Document");
            ir.setGuiId(gui_id);
            IntegrationSection[] sections = new IntegrationSection[1];
            IntegrationSection detailSec = new IntegrationSection(section_name);
            IntegrationField[] ifs = new IntegrationField[1];
            IntegrationField field1 = new IntegrationField("DM_FILE_SIZE", value);
            ifs[0] = field1;
            detailSec.setFields(ifs);
            sections[0] = detailSec;
            ir.setSections(sections);
            IntegrationRecord[] intRecs = new IntegrationRecord[]{ir};
            ResponseHelperHeader rhh = tws.saveRecord(intRecs);
            log.debug("calc helper - success: " + rhh.getSuccessful() + ", failed: " + rhh.getFailed() + ", total: " + rhh.getTotal());
            ResponseHelper r = rhh.getResponseHelpers()[0];
            log.debug("Updated Document record ID: " + r.getRecordId());
        } catch (Exception var18) {
            log.error("Unable to update Document: " + var18.getMessage());
        }

    }
    public static void getFileDetails(TririgaWS tws, long recordId) {
        Content content = new Content(recordId, (DataHandler)null, (String)null, String.valueOf(Long.toString(recordId)) + "_" + UUID.randomUUID() + ".del", (String)null);

        try {
            Response response = tws.download(content);
            fileName = response.getFileName();
            fileSize = response.getLength().toString();
        } catch (Exception var6) {
            log.error("Unable to get file name of content from spec_id [" + recordId + "] => " + var6.toString());
        }

    }
    public static InputStream docDownload(TririgaWS tws, long recordId, String binaryFieldName) {
        Content content = new Content(recordId, (DataHandler)null, binaryFieldName, Long.toString(recordId) + "_" + UUID.randomUUID() + ".del", (String)null);
        InputStream docStream = null;

        String m;
        try {
            Response response = tws.download(content);
            if (response != null && response.getLength() != 0L) {
                docStream = response.getContent().getInputStream();
                log.error("Content InputStream retrieved from TRIRIGA API call download() OK");
            } else {
                m = "TRIRIGA API call download() response is NULL - no InputStream content for ";
                if (binaryFieldName == null) {
                    m = m + "Document";
                } else {
                    m = m + "binary field [" + binaryFieldName + "]";
                }

                m = m + " - spec_id [" + recordId + "]";
                log.error(m);
            }
        } catch (Exception var8) {
            m = "Exception getting Content InputStream for ";
            if (binaryFieldName == null) {
                m = m + "Document";
            } else {
                m = m + "binary field [" + binaryFieldName + "]";
            }

            m = m + " - spec_id [" + recordId + "]: " + var8.getMessage();
            log.error(m);
        }

        return docStream;
    }

}
