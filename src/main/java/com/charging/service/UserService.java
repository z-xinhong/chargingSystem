public interface UserService {
    Result register(RegisterDTO dto);
    Result login(LoginDTO dto);
}