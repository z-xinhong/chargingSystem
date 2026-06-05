public interface ChargingRequestService {
    Result submit(Long userId, RequestDTO dto);
    Result cancel(Long requestId);
    Result status(Long requestId);
}