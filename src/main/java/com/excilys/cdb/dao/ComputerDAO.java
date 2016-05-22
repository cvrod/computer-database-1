package com.excilys.cdb.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.excilys.cdb.jdbc.ConnectionManager;
import com.excilys.cdb.jdbc.ConnectionMySQLFactory;
import com.excilys.cdb.jdbc.ITransactionManager;
import com.excilys.cdb.jdbc.TransactionManager;
import com.excilys.cdb.mapper.ComputerMapper;
import com.excilys.cdb.mapper.LocalDateMapper;
import com.excilys.cdb.mapper.MapperException;
import com.excilys.cdb.model.Computer;
import com.excilys.cdb.model.PageParameters;
import com.excilys.cdb.model.PageParameters.Order;

/**
 * Singleton for the ComputerDAO.
 *
 * implements all the CRUD operations defined in DAO<>.
 *
 * @author simon
 *
 */
@Repository
public class ComputerDAO implements DAO<Computer> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ComputerDAO.class);

	@Autowired
	private ComputerMapper mapper;

	@Autowired
	private LocalDateMapper dateMapper;

	@Autowired
	private ConnectionManager connectionManager;

	private static final String FIND_BY_ID = "SELECT c.id, c.name, c.introduced, c.discontinued, c.company_id, o.name as company_name FROM computer c LEFT JOIN company o on c.company_id=o.id WHERE c.id=?";

	private static final String CREATE = "INSERT INTO computer (name, introduced, discontinued, company_id) VALUES(?, ?, ?, ?)";

	private static final String UPDATE = "UPDATE computer SET name=?, introduced=?, discontinued=?, company_id=? WHERE id=?";

	private static final String DELETE = "DELETE FROM computer WHERE id=?";

	private static final String DELETE_LIST = "DELETE FROM computer WHERE id IN (%s)";

	private static final String FIND_ALL = "SELECT c.id, c.name, c.introduced, c.discontinued, c.company_id, o.name as company_name FROM computer c LEFT JOIN company o ON c.company_id=o.id";

	private static final String FIND_ALL_BETTER = "SELECT B.id, B.name, B.introduced, B.discontinued, B.company_id, C.name as company_name FROM (SELECT id FROM computer WHERE name like ? ORDER BY %s %s LIMIT ?, ?) A LEFT JOIN computer B on B.id=A.id LEFT JOIN company C on B.company_id=C.id";

	private static final String FIND_ALL_BETTER_NO_SEARCH = "SELECT B.id, B.name, B.introduced, B.discontinued, B.company_id, C.name as company_name FROM (SELECT id FROM computer ORDER BY %s %s LIMIT ?, ?) A LEFT JOIN computer B on B.id=A.id LEFT JOIN company C on B.company_id=C.id";
	
	private static final String COUNT = "SELECT count(id) as nb FROM computer";

	private static final String COUNT_SEARCH = "SELECT count(name) as nb FROM computer WHERE name like ?";

	private static final String DELETE_COMPUTER = "DELETE FROM computer WHERE company_id=?";

	@Override
	public Computer find(Long id) {

		Computer computer = null;

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {

			con = this.connectionManager.getConnection();

			stmt = con.prepareStatement(FIND_BY_ID);

			this.setParams(stmt, id);

			rs = stmt.executeQuery();

			if (rs.first()) {

				computer = this.mapper.map(rs);

				ComputerDAO.LOGGER.info("succefully found computer of id : " + id);
			} else {
				ComputerDAO.LOGGER.warn("couldn't find computer of id : " + id);
			}

		} catch (SQLException | MapperException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return computer;
	}

	@Override
	public Computer create(Computer obj) {

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {

			con = this.connectionManager.getConnection();

			stmt = con.prepareStatement(CREATE, Statement.RETURN_GENERATED_KEYS);

			Timestamp introduced = this.dateMapper.toTimestamp(obj.getIntroduced());

			Timestamp discontinued = this.dateMapper.toTimestamp(obj.getDiscontinued());

			Long companyId = obj.getCompany() == null ? null : obj.getCompany().getId();

			this.setParams(stmt, obj.getName(), introduced, discontinued, companyId);

			int res = stmt.executeUpdate();

			if (res > 0) {
				rs = stmt.getGeneratedKeys();
				if (rs.first()) {
					obj.setId(rs.getLong(1));
					ComputerDAO.LOGGER.info("successfully created computer : " + obj.toString());
				} else {
					ComputerDAO.LOGGER.error("Computer created but no ID could be obtained.");
					throw new DAOException("Computer created but no ID could be obtained.");
				}

			} else {
				ComputerDAO.LOGGER.warn("Could not create computer : " + obj.toString());
				throw new DAOException("Could not create computer.");
			}

		} catch (SQLException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return obj;
	}

	@Override
	public Computer update(Computer obj) {

		Connection con = null;
		PreparedStatement stmt = null;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(UPDATE);

			Timestamp introduced = this.dateMapper.toTimestamp(obj.getIntroduced());

			Timestamp discontinued = this.dateMapper.toTimestamp(obj.getDiscontinued());

			Long companyId = obj.getCompany() == null ? null : obj.getCompany().getId();

			this.setParams(stmt, obj.getName(), introduced, discontinued, companyId, obj.getId());

			int res = stmt.executeUpdate();

			if (res > 0) {
				ComputerDAO.LOGGER.info("Successfully updated computer : " + obj.toString());
			} else {
				ComputerDAO.LOGGER.warn("Could not update computer : " + obj.toString());
			}

		} catch (SQLException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt);
		}

		return obj;
	}

	@Override
	public void delete(Computer obj) {

		Connection con = null;
		PreparedStatement stmt = null;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(DELETE);

			this.setParams(stmt, obj.getId());

			int res = stmt.executeUpdate();

			if (res > 0) {
				ComputerDAO.LOGGER.info("successfully deleted computer : " + obj.toString());
			} else {
				ComputerDAO.LOGGER.warn("couldn't delete computer : " + obj.toString());
			}

		} catch (SQLException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt);
		}
	}

	/**
	 * Delete computers based on their company.
	 *
	 * @param id
	 *            id of the company to whom the computers to delete belong.
	 */
	public void deleteByCompanyId(Long id) {

		PreparedStatement stmt = null;
		Connection con = null;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(DELETE_COMPUTER);

			this.setParams(stmt, id);

			stmt.executeUpdate();

		} catch (SQLException e) {
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt);
		}
	}

	@Override
	public void deleteAll(List<Long> objs) {

		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < objs.size(); i++) {
			builder.append(objs.get(i) + ",");
		}

		String s = String.format(DELETE_LIST, builder.deleteCharAt(builder.length() - 1).toString());

		Connection con = null;
		PreparedStatement stmt = null;

		try {
			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(s);

			stmt.executeUpdate();

		} catch (SQLException e) {
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt);
		}
	}

	@Override
	public List<Computer> findAll() {

		ArrayList<Computer> result = new ArrayList<>();

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(FIND_ALL);

			rs = stmt.executeQuery();

			while (rs.next()) {

				Computer computer = this.mapper.map(rs);

				result.add(computer);

			}

			if (result.size() > 0) {
				ComputerDAO.LOGGER.info("successfully retrieved " + result.size() + " computer(s)");
			} else {
				ComputerDAO.LOGGER.warn("couldn't retrieve any computers");
			}

		} catch (SQLException | MapperException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return result;
	}

	@Override
	public List<Computer> findAll(PageParameters page) {

		ArrayList<Computer> result = new ArrayList<>();

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {

			con = this.connectionManager.getConnection();

			String search = page.getSearch() == null ? "" : page.getSearch();

			stmt = con.prepareStatement(String.format(search.isEmpty() ? FIND_ALL_BETTER_NO_SEARCH : FIND_ALL_BETTER,
					page.getOrder().toString(), page.getDirection().toString()));

			if (search.isEmpty()) {
				this.setParams(stmt, page.getSize() * page.getPageNumber(), page.getSize());
			} else {
				this.setParams(stmt, search + "%", page.getSize() * page.getPageNumber(), page.getSize());
			}

			rs = stmt.executeQuery();

			while (rs.next()) {
				result.add(this.mapper.map(rs));
			}

			if (result.size() > 0) {
				ComputerDAO.LOGGER.info("successfully retrieved " + result.size() + " computer(s)");
			} else {
				ComputerDAO.LOGGER.warn("couldn't retrieve any computers");
			}

		} catch (SQLException | MapperException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return result;
	}

	/**
	 * Count number of computers using a page parameters.
	 *
	 * @param page
	 *            parameters for the query.
	 * @return number of computers.
	 */
	public long count(PageParameters page) {

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		long nb = 0;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(COUNT_SEARCH);

			this.setParams(stmt, page.getSearch() + "%");

			rs = stmt.executeQuery();

			if (rs.first()) {
				nb = rs.getLong("nb");
			}

		} catch (SQLException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return nb;
	}

	@Override
	public long count() {

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		long nb = 0;

		try {

			con = this.connectionManager.getConnection();
			stmt = con.prepareStatement(COUNT);

			rs = stmt.executeQuery();

			if (rs.first()) {
				nb = rs.getLong("nb");
			}

		} catch (SQLException e) {
			ComputerDAO.LOGGER.error(e.getMessage());
			throw new DAOException(e);
		} finally {
			this.closeAll(stmt, rs);
		}

		return nb;
	}
}
