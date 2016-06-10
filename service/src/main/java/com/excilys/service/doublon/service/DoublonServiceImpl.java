package com.excilys.service.doublon.service;

import com.excilys.binding.doublon.Levenshtein;
import com.excilys.binding.doublon.SimilarityCalculator;
import com.excilys.binding.mapper.IComputerMapper;
import com.excilys.core.doublon.model.Conflict;
import com.excilys.core.doublon.model.Rapport;
import com.excilys.core.dto.ComputerDTO;
import com.excilys.service.service.IComputerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service("doublonService")
public class DoublonServiceImpl implements DoublonService {

    @Autowired
    private IComputerService computerService;

    @Autowired
    private IComputerMapper computerMapper;

    private static final double TO_CHECK = 60.0;
    private static final double TO_REFUSE = 70.0;

    private SimilarityCalculator levenshtein = new Levenshtein();

    @Override
    public Rapport getRapport(List<ComputerDTO> computers) {
        List<ComputerDTO> computersInBase = computerMapper.toDTO(this.computerService.getAll());
        Rapport retVal = new Rapport();
        for (ComputerDTO computerOut : computers) {
            List<ComputerDTO> tList = check(computersInBase, computerOut, this.TO_CHECK);
            if (tList.isEmpty()) {
                tList.addAll(check(computersInBase, computerOut, this.TO_REFUSE));
                if (tList.isEmpty()) {
                    retVal.getToImport().add(computerOut);
                } else {
                    retVal.getToCheck().add(new Conflict(computerOut, tList));
                }
            } else {
                retVal.getRefuse().add(new Conflict(computerOut, tList));
            }
        }
        return retVal;
    }

    /**
     * Use to check all the elements of the computers to check if we should import them in the database or not.
     *
     * @param computerIns to check
     * @param computerOut to check
     * @param rule        to use to check
     * @return the list of computer where we have a conflicts
     */
    private List<ComputerDTO> check(List<ComputerDTO> computerIns, ComputerDTO computerOut, double rule) {
        List<ComputerDTO> retVal = new ArrayList<>();
        for (ComputerDTO computerIn : computerIns) {
            if (levenshtein.getPercentSimilarity(computerIn.getName(), computerOut.getName()) >= rule) {
                retVal.add(computerIn);
                break;
            }
        }
        return retVal;
    }

}
