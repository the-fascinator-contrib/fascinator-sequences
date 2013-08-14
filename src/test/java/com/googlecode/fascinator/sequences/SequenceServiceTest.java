/*
 * The Fascinator - Sequence Service Test
 * Copyright (C) 2008-2010 University of Southern Queensland
 * Copyright (C) 2012 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.googlecode.fascinator.sequences;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.fascinator.common.JsonObject;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.sequences.SequenceService;



public class SequenceServiceTest {
    
	private SequenceService sequenceService;
    
    @Before
    public void startup() throws IOException, SQLException {
    	sequenceService = new SequenceService();
    	
    	JsonObject jsonObject = new JsonObject();
    	JsonObject dataBaseServices = new JsonObject();
    	dataBaseServices.put("derbyHome",  "derby");
    	jsonObject.put("database-service", dataBaseServices);
    	JsonSimple jsonSimple = new JsonSimple(jsonObject);
    	sequenceService.init(jsonSimple);
    }
    
    @After
    public void shutdown() throws IOException, SQLException {
    	sequenceService.shutdown();
    	File derbyDir = new File("derby");
    	FileUtils.deleteDirectory(derbyDir);
    }
    
	@Test
	public void testGetSequence() throws SQLException {
		Assert.assertEquals(1L,sequenceService.getSequence("sampleSequence1").longValue());
		Assert.assertEquals(2L,sequenceService.getSequence("sampleSequence1").longValue());
		Assert.assertEquals(1L,sequenceService.getSequence("sampleSequence2").longValue());
		Assert.assertEquals(3L,sequenceService.getSequence("sampleSequence1").longValue());
		Assert.assertEquals(2L,sequenceService.getSequence("sampleSequence2").longValue());
		Assert.assertEquals(4L,sequenceService.getSequence("sampleSequence1").longValue());
	}

}
