@Data
@TableName("charging_request")
public class ChargingRequest {
    private Long id;
    private Long userId;
    private String mode; // FAST / SLOW
    private Double requestedKwh;
    private String queueNumber;
    private String queueType;
    private String status;
}