package com.ociweb.jfast.generator;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ociweb.jfast.catalog.loader.ClientConfig;
import com.ociweb.jfast.catalog.loader.TemplateCatalogConfig;
import com.ociweb.jfast.catalog.loader.TemplateLoader;
import com.ociweb.jfast.primitive.PrimitiveReader;
import com.ociweb.jfast.stream.FASTDecoder;
import com.ociweb.jfast.stream.FASTListener;
import com.ociweb.jfast.stream.FASTReaderInterpreterDispatch;
import com.ociweb.jfast.stream.FASTReaderReactor;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;

public class DispatchLoaderTest {

    private final class DemoFASTListener implements FASTListener {
		private final FASTReaderReactor[] reactor;
		private final AtomicInteger records;
		private final int switchToCompiled2;
		private final int exitTest;
		private final byte[] catalog1;
		private final FASTDecoder[] decoder;
		private final byte[] catalog2;
		private final PrimitiveReader reader;
		private final int switchToCompiled1;
		private final AtomicBoolean alive;
		Pipe queue = null;

		private DemoFASTListener(FASTReaderReactor[] reactor,
				AtomicInteger records, int switchToCompiled2, int exitTest,
				byte[] catalog1, FASTDecoder[] decoder, byte[] catalog2,
				PrimitiveReader reader, int switchToCompiled1,
				AtomicBoolean alive) {
			this.reactor = reactor;
			this.records = records;
			this.switchToCompiled2 = switchToCompiled2;
			this.exitTest = exitTest;
			this.catalog1 = catalog1;
			this.decoder = decoder;
			this.catalog2 = catalog2;
			this.reader = reader;
			this.switchToCompiled1 = switchToCompiled1;
			this.alive = alive;
		}

		@Override
		public void fragment(int templateId, Pipe queue) {
		         this.queue = queue;

		}

		@Override
		public void fragment() {
		    if (null!=queue) {
		        
		        int id = PipeReader.readInt(queue, MESSAGE_ID_IDX);
		      //  System.err.println(templateId+" "+id);
		        
		        String version = PipeReader.readASCII(queue, VERSION_IDX, new StringBuilder()).toString();

		       if (records.intValue()<switchToCompiled1) {
		           //Interpreter
		           assertEquals("1.0",version);
		       } else if (records.intValue()<switchToCompiled2) {
		           //Compiled
		           assertEquals("1.0",version);
		       } else if (records.intValue()<exitTest) {
		           //Compiled 2
		           assertEquals("2.0",version);
		       }               
               //Dump
               Pipe.publishBlobWorkingTailPosition(queue, Pipe.getBlobRingHeadPosition(queue));
               Pipe.publishWorkingTailPosition(queue, Pipe.headPosition(queue));
               ////
		       records.incrementAndGet();
		       
		       if (records.intValue()==switchToCompiled1) {
		           decoder[0] = DispatchLoader.loadDispatchReader(catalog1, TemplateCatalogConfig.buildRingBuffers(new TemplateCatalogConfig(catalog1), (byte)8, (byte)18));
		           reactor[0] = new FASTReaderReactor(decoder[0],reader);
		          // queue = decoder[0].ringBuffer(0);
		           System.err.println("Created new "+decoder.getClass().getSimpleName());
		       }
		       if (records.intValue()==switchToCompiled2) {
		           decoder[0] = DispatchLoader.loadDispatchReader(catalog2, TemplateCatalogConfig.buildRingBuffers(new TemplateCatalogConfig(catalog2), (byte)8, (byte)18));
		           reactor[0] = new FASTReaderReactor(decoder[0],reader);
		         //  queue = decoder[0].ringBuffer(0);
		           System.err.println("Created new "+decoder.getClass().getSimpleName());
		       }
		       if (records.intValue()>exitTest) {
		           alive.set(false);
		       }                    
		        
                //Dump
                Pipe.publishBlobWorkingTailPosition(queue, Pipe.getBlobRingHeadPosition(queue));
                Pipe.publishWorkingTailPosition(queue, Pipe.headPosition(queue));
                ////
		    }
		    queue = null;
		}
	}

	final int PREAMBLE_IDX = 0;
    final int MESSAGE_ID_IDX = 1;
    final int VERSION_IDX = 2;
       
    @Test
    public void testClassReplacement() {        
        
        //These two are the same except for the internal version number
        final byte[] catalog1=buildRawCatalogData("/performance/example.xml");
        final byte[] catalog2=buildRawCatalogData("/performance/example2.xml");
                
        final PrimitiveReader reader = buildReader("/performance/complex30000.dat");

        //setup
        final int switchToCompiled1 = 50;
        final int switchToCompiled2 = 100;
        final int exitTest = 150;
        FASTClassLoader.deleteFiles();
        
        //Base class reference, known at static compile time.        
        final FASTDecoder[] decoder = new FASTDecoder[1]; 
        decoder[0] = new FASTReaderInterpreterDispatch(catalog1, TemplateCatalogConfig.buildRingBuffers(new TemplateCatalogConfig(catalog1), (byte)8, (byte)18));
        System.err.println("Created new "+decoder.getClass().getSimpleName());
        
        
        final AtomicInteger records = new AtomicInteger();
        final FASTReaderReactor[] reactor = new FASTReaderReactor[1];
        final FASTListener[] listener = new FASTListener[1];
        final AtomicBoolean alive = new AtomicBoolean(true);
        
        //This test can only be fixed after we establish when the switchovers are to happen.
        
        listener[0] = new DemoFASTListener(reactor, records, switchToCompiled2, exitTest,
				catalog1, decoder, catalog2, reader, switchToCompiled1, alive);
        
        reactor[0] = new FASTReaderReactor(decoder[0], reader);
                
        //Removed test for now until API is finished changing
//        records.set(0);
//        //Non-Blocking reactor select
//        while (alive.get() &&  (0!=(reactor[0].select()))) {                 
//        }
  
    }


    private PrimitiveReader buildReader(String name) {
        URL sourceData = getClass().getResource(name);
        File sourceDataFile = new File(sourceData.getFile().replace("%20", " "));      
        PrimitiveReader reader = new PrimitiveReader(buildBytesForTestingByteArray(sourceDataFile));
        return reader;
    }
    
    
    static byte[] buildRawCatalogData(String resourceName) {

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setPreableBytes((short)4);
        
        ByteArrayOutputStream catalogBuffer = new ByteArrayOutputStream(4096);
        try {
            TemplateLoader.buildCatalog(catalogBuffer, resourceName, clientConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return catalogBuffer.toByteArray();
    }

    static byte[] buildBytesForTestingByteArray(File fileSource) {
        byte[] fileData = null;
        try {
            // do not want to time file access so copy file to memory
            fileData = new byte[(int) fileSource.length()];
            FileInputStream inputStream = new FileInputStream(fileSource);
            int readBytes = inputStream.read(fileData);
            inputStream.close();
            assertEquals(fileData.length, readBytes);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileData;
    }
}
