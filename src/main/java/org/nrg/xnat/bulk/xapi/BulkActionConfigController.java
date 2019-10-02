// Copyright 2019 Radiologics, Inc
// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnat.bulk.xapi;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.services.SerializerService;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.XFTTable;
import org.nrg.xft.security.UserI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@XapiRestController
@RequestMapping(value = "/bulkaction")
@Api(description = "Bulk Processing Action Management API")
public class BulkActionConfigController extends AbstractXapiProjectRestController {
	@Autowired
	public BulkActionConfigController(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final ConfigService configService, final SerializerService serializer, final JdbcTemplate jdbcTemplate) {
		super(userManagementService, roleHolder);
		_configService = configService;
		_serializer = serializer;
		_jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
	}


	
	@ApiOperation(value = "Gets the actionSpecification configurations for a given datatype" )
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/xsiType/{xsiType}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
	public ResponseEntity<JsonNode> getactions(@PathVariable("xsiType") final String xsiType) throws Exception{
		final UserI user = getSessionUser();

		final List<Configuration> configs = _configService.getConfigsByTool(BULK_ACTION_TOOLNAME);
		if (configs == null || configs.size() < 1) {
			return new ResponseEntity<JsonNode>(HttpStatus.NOT_FOUND);
		}
		List<Configuration> configsFilteredByXsiType = new ArrayList<Configuration>();
        XFTTable table = new XFTTable();
        table.initTable(configColumns);  //"tool","path","project","user","create_date","reason","contents", "unversioned", "version", "status"};
		ObjectMapper objectMapper = new ObjectMapper();

		for (Configuration conf: configs) {
			final String config = conf != null ? conf.getContents() : null;
			//HACK - to avoid escaped String being sent as response
			if (StringUtils.isNotBlank(config)) {
				try {
					JsonNode node = objectMapper.readTree(config);
					JsonNode nodeDetails = node.get(ACTION_SPECIFICATION_ROOT);
					if (nodeDetails != null) {
						JsonNode xsiTypeNode = nodeDetails.get(ACTION_SPECIFICATION_XSITYPE);
						if (xsiTypeNode != null) {
							if (xsiTypeNode.textValue() != null && xsiTypeNode.textValue().equals(xsiType)) {
								if (!"disabled".equals(conf.getStatus()))
									configsFilteredByXsiType.add(conf);
							}
						}
					}
				}catch(Exception e) {
				}
			}
		}
		for (Configuration c: configsFilteredByXsiType) {
				String[] scriptArray = {
                        c.getTool(),
                        c.getPath(),
                        c.getXnatUser(),
                        c.getCreated().toString(),
                        c.getContents(),
                        Boolean.toString(c.isUnversioned()),
                        Integer.toString(c.getVersion()),
                        c.getStatus()
                };
                table.insertRow(scriptArray);
		}
		OutputStream baos = null;
		InputStream is = null;
		try {
			baos = new ByteArrayOutputStream();
			OutputStreamWriter sw = new OutputStreamWriter(baos);
			BufferedWriter writer = new BufferedWriter(sw);

		    writer.write("{\"ResultSet\":{" );
		    writer.write("\"Result\":");
			table.toJSON(writer, new HashMap<String, Map<String, String>>());
			writer.write("}}");
			writer.flush();
			//Read the node from this stream
			is = new ByteArrayInputStream(((ByteArrayOutputStream)baos).toByteArray());
			JsonNode node = objectMapper.readTree(is);
			return new ResponseEntity<>(node,  HttpStatus.OK);
		}catch(Exception e) {
			return new ResponseEntity<JsonNode>(HttpStatus.INTERNAL_SERVER_ERROR);
		}finally{
			if (baos != null) baos.close();
			if (is != null) is.close();
		}
	}

/*
	@ApiOperation(value = "Save search to track status" )
	@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error"),@ApiResponse(code = 200, message = "Saved")})
    @XapiRequestMapping(value = "/save", method = RequestMethod.POST,consumes = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity saveBulkSearch() throws Exception{
		final UserI user = getSessionUser();
		try {
			//Read the XML sent over
			//Extract its bundle-id
			//Save entry
			
		}catch(Exception e) {
			return new ResponseEntity<JsonNode>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
	}	*/
	
	final private String BULK_ACTION_TOOLNAME = "actionSpecification";
	final private String ACTION_SPECIFICATION_ROOT = "Action";
	final private String ACTION_SPECIFICATION_XSITYPE = "xsiType";
    private static final String[] configColumns = {"tool", "path",  "user", "create_date", "contents", "unversioned", "version", "status"};

	private final ConfigService              _configService;
	private final SerializerService          _serializer;
	private final NamedParameterJdbcTemplate _jdbcTemplate;
}