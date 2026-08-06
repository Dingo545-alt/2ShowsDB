package DataAccessObject.Interfaces;

import Model.Director;

public interface DirectorDao {

    Director getDirectorById(String id);
}