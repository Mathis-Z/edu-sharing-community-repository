package org.edu_sharing.repository.server.jobs.quartz;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.edu_sharing.repository.server.importer.PersistentHandlerEdusharing;
import org.edu_sharing.repository.server.importer.RecordHandlerInterfaceBase;
import org.edu_sharing.repository.server.importer.sax.ListIdentifiersHandler;
import org.edu_sharing.repository.server.importer.sax.RecordHandlerLOM;


/**
 * @author rudi
 * @TODO: learnline (serientitel? edmond bilder?) 
 *        sind die replIDs Pool übergreifend eindeutig???? nur dann funktioniert PersistentHandlereduSharing.mustBePersisted(String, String)
 *        test ob anzahl der datensätze übereinstimmen
 *        
 *        RemoveDeletedImports: könnte man beschleunigen wenn sodis wie im edmond pool gelöschte Datensätze bei ListIdentifiers markiert mit:
 *        	<header status="deleted">
 */
public class ImporterJobSAX extends ImporterJob {
	
	
	@Override
	protected void start(String urlImport, String oaiBaseUrl, String metadataSetId, String metadataPrefix, String[] sets, String recordHandlerClass, String binaryHandlerClass, String importerClass, String[] idList) {
		try{
			for(String set: sets){
				long millisec = System.currentTimeMillis();
				logger.info("starting import");
				
				RecordHandlerInterfaceBase recordHandler = null;
				
				if(recordHandlerClass != null){
					Class tClass = Class.forName(recordHandlerClass);
					Constructor constructor = tClass.getConstructor(String.class);
					recordHandler = (RecordHandlerInterfaceBase)constructor.newInstance(metadataSetId);
				}else{
					recordHandler = new RecordHandlerLOM(metadataSetId);
				}
				
				new ListIdentifiersHandler(recordHandler, new PersistentHandlerEdusharing(this), null, oaiBaseUrl, set, metadataPrefix, metadataSetId);
				logger.info("finished import in "+(System.currentTimeMillis() - millisec)/1000+" secs");
			}
		}catch(Throwable e){
			logger.error(e.getMessage(), e);
		}
	}

	@Override
	public Class[] getJobClasses() {
		List<Class> classList = new ArrayList<Class>(Arrays.asList(allJobs));
		classList.add(ImporterJobSAX.class);
		return classList.toArray(new Class[classList.size()]);
	}
}
