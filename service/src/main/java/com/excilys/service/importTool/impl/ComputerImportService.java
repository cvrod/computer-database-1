package com.excilys.service.importTool.impl;

import au.com.bytecode.opencsv.CSVReader;
import com.excilys.binding.validation.ComputerValidator;
import com.excilys.core.conflict.ImportException;
import com.excilys.core.conflict.Rapport;
import com.excilys.core.conflict.format.Error;
import com.excilys.core.conflict.format.ErrorMessage;
import com.excilys.core.conflict.format.Fields;
import com.excilys.core.dto.ComputerDTO;
import com.excilys.service.importTool.IComputerImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ComputerImportService implements IComputerImportService {

    @Autowired
    private ComputerValidator computerValidator;

    @Override
    public Rapport importComputersFromCSV(MultipartFile file) {
        Rapport rapport = new Rapport();
        File convFile = new File(file.getOriginalFilename());

        try {
            file.transferTo(convFile);
        } catch (IOException e) {
            throw new ImportException("Error converting file", e);
        }

        try {
            FileReader fr = new FileReader(convFile);
            CSVReader csvReader = new CSVReader(fr, ',');
            String[] nextLine;

            while ((nextLine = csvReader.readNext()) != null) {
                String name = nextLine[0];
                String introducedString = nextLine[1];
                String discontinuedString = nextLine[2];
                String companyName = nextLine[3];

                ComputerDTO computerDTO = new ComputerDTO.Builder()
                        .name(name.trim())
                        .introduced(introducedString.trim())
                        .discontinued(discontinuedString.trim())
                        .companyName(companyName.trim())
                        .build();

                Map<Fields, List<ErrorMessage>> errors = computerValidator.validateComputerDTO(computerDTO);
                Error conflict = new Error();

                if (errors.size() != 0) {
                    conflict.setComputerDTO(computerDTO);
                    conflict.setErrorMap(errors);
                    rapport.getRefuse().add(conflict);
                } else {
                    rapport.getToImport().add(computerDTO);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rapport;
    }

    @Override
    public Rapport importComputersFromXML(MultipartFile file) {
        File convFile = new File(file.getOriginalFilename());
        try {
            file.transferTo(convFile);
        } catch (IOException e) {
            throw new ImportException("Error converting file", e);
        }
        return mapDatas(readFile(convFile));
    }

    private Rapport mapDatas(NodeList elts) {
        Rapport rapport = new Rapport();

        for (int i = 0; i < elts.getLength(); i++) {
            Node nNode = elts.item(i);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                ComputerDTO temp = new ComputerDTO();
                temp.setName(eElement.getElementsByTagName("name").item(0).getTextContent());
                temp.setIntroduced(eElement.getElementsByTagName("introduced").item(0).getTextContent());
                temp.setDiscontinued(eElement.getElementsByTagName("discontinued").item(0).getTextContent());
                temp.setCompanyName(eElement.getElementsByTagName("company_name").item(0).getTextContent());

                Map<Fields, List<ErrorMessage>> err = computerValidator.validateComputerDTO(temp);
                if (err.size() == 0) {
                    rapport.getToImport().add(temp);
                } else {
                    Error conflict = new Error();
                    conflict.setComputerDTO(temp);
                    conflict.setErrorMap(err);
                    rapport.getRefuse().add(conflict);
                }
            }
        }
        return rapport;
    }

    private NodeList readFile(File file) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new ImportException("Error creating Document Builder", e);
        }
        Document doc;
        try {
            doc = dBuilder.parse(file);
        } catch (SAXException | IOException e) {
            throw new ImportException("Error parsing file", e);
        }

        doc.getDocumentElement().normalize();

        return doc.getElementsByTagName("computer");
    }
}
