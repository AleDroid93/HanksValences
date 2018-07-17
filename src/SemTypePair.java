import java.util.Objects;

public class SemTypePair {
    private String semType1;
    private String semType2;

    public SemTypePair(String semType1) {
        this.semType1 = semType1;
        this.semType2 = "";
    }

    public SemTypePair() {
        this.semType1 = "";
        this.semType2 = "";
    }

    public SemTypePair(String semType1, String semType2) {
        this.semType1 = semType1;
        this.semType2 = semType2;
    }

    public String getSemType1() {
        return semType1;
    }

    public void setSemType1(String semType1) {
        this.semType1 = semType1;
    }

    public String getSemType2() {
        return semType2;
    }

    public void setSemType2(String semType2) {
        this.semType2 = semType2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemTypePair)) return false;
        SemTypePair that = (SemTypePair) o;
        return Objects.equals(getSemType1(), that.getSemType1()) &&
                Objects.equals(getSemType2(), that.getSemType2());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getSemType1(), getSemType2());
    }

    @Override
    public String toString() {
        return "SemTypePair{" +
                "semantic type 1 ='" + semType1 + '\'' +
                ", semantic type 2='" + semType2 + '\'' +
                '}';
    }
}
