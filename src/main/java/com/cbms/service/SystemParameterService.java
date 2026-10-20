package com.cbms.service;

import com.cbms.dto.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SystemParameterService {
    private final JdbcTemplate jdbc;

    public SystemParameterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void validate(String value, String type) {
        if (value == null || value.isBlank() || value.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value");
        try {
            switch (type) {
                case "STRING" -> {
                }
                case "INTEGER" -> Long.parseLong(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> {
                    if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException();
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value does not match parameter type");
        }
    }

    private String actor(String name) {
        return name == null || name.isBlank() ? "UNKNOWN" : name;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("select param_key, param_value, value_type, description, crt_dt, crt_by, upd_dt, upd_by from public.bank_system_parameter order by param_key");
    }

    public Map<String, Object> get(String key) {
        return jdbc.queryForList("select param_key, param_value, value_type, description, crt_dt, crt_by, upd_dt, upd_by from public.bank_system_parameter where param_key=?", key).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parameter not found"));
    }

    @Transactional
    public Map<String, Object> create(SystemParameterRequest req, String username) {
        validate(req.value(), req.valueType());
        try {
            jdbc.update("insert into public.bank_system_parameter(param_key,param_value,value_type,description,crt_by,upd_by) values(?,?,?,?,?,?)", req.key(), req.value(), req.valueType(), req.description(), actor(username), actor(username));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parameter already exists");
        }
        jdbc.update("insert into public.bank_system_parameter_history(param_key,action,new_value,changed_by) values(?,'CREATE',?,?)", req.key(), req.value(), actor(username));
        return get(req.key());
    }

    @Transactional
    public Map<String, Object> update(String key, SystemParameterUpdateRequest req, String username) {
        var existing = jdbc.queryForList("select param_value,value_type from public.bank_system_parameter where param_key=? for update", key).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parameter not found"));
        validate(req.value(), (String) existing.get("value_type"));
        jdbc.update("update public.bank_system_parameter set param_value=?, description=?,upd_dt=current_timestamp,upd_by=? where param_key=?", req.value(), req.description(), actor(username), key);
        jdbc.update("insert into public.bank_system_parameter_history(param_key,action,old_value,new_value,changed_by) values(?,'UPDATE',?,?,?)", key, existing.get("param_value"), req.value(), actor(username));
        return get(key);
    }

    public List<Map<String, Object>> history(String key) {
        get(key);
        return jdbc.queryForList("select history_id,param_key,action,old_value,new_value,changed_dt,changed_by from public.bank_system_parameter_history where param_key=? order by history_id desc limit 200", key);
    }
}
