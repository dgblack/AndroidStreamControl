using UnityEngine;

public class ClickCapture : MonoBehaviour
{
    public delegate void OnGestureCallback(Vector2 startPos, Vector2 endPos, float duration);
    public OnGestureCallback callback;

    private bool wasDown = false;
    private long tStart;
    private Vector2 pStart = Vector2.zero;
    private Vector2[] screenCorners;

    // Start is called before the first frame update
    void Start()
    {
        // Clockwise
        screenCorners = new Vector2[4];
        screenCorners[0] = new Vector2(transform.position.x + transform.localScale.x / 2, transform.position.y + transform.localScale.y / 2);
        screenCorners[1] = new Vector2(transform.position.x + transform.localScale.x / 2, transform.position.y - transform.localScale.y / 2);
        screenCorners[2] = new Vector2(transform.position.x - transform.localScale.x / 2, transform.position.y - transform.localScale.y / 2);
        screenCorners[3] = new Vector2(transform.position.x - transform.localScale.x / 2, transform.position.y + transform.localScale.y / 2);
    }

    // Update is called once per frame
    void Update()
    {
        // Gesture started
        if (!wasDown && Input.GetMouseButtonDown(0))
        {
            RaycastHit hit;
            if (Physics.Raycast(Camera.main.ScreenPointToRay(Input.mousePosition), out hit))
            {
                Vector3 mp = hit.point;
                if (IsInside(mp))
                {
                    wasDown = true;
                    tStart = Timer.GetTime();
                    pStart = new Vector2(mp.x, mp.y);
                }
            }
        }
        // Gesture completed
        else if (wasDown && !Input.GetMouseButton(0))
        {
            wasDown = false;
            SendGestureMsg();
        }
    }

    void SendGestureMsg()
    {
        long tEnd = Timer.GetTime();

        RaycastHit hit;
        if (Physics.Raycast(Camera.main.ScreenPointToRay(Input.mousePosition), out hit))
        {
            Vector3 mp = hit.point;
            Vector2 pEnd = new Vector2(mp.x, mp.y);

            if (!IsInside(mp))
            {
                Vector2 dp = pEnd - pStart;
                float[,] A = new float[2, 2];
                float[,] Ainv = new float[2, 2];
                A[0, 0] = dp.x; A[1, 0] = dp.y;

                // If gesture ended off the phone screen, project back to the screen
                for (int i = 0; i < 4; i++)
                {
                    Vector2 dv = screenCorners[(i + 1) % 4] - screenCorners[i];
                    Vector2 pmv = screenCorners[i] - pStart;
                    A[0, 1] = -dv.x; A[1, 1] = -dv.y;

                    // Check determinant of A
                    float detA = A[0, 0] * A[1, 1] - A[0, 1] * A[1, 0];
                    if (detA != 0)
                    {
                        // Calculate inverse
                        Ainv[0, 0] = A[1, 1] / detA;
                        Ainv[1, 1] = A[0, 0] / detA;
                        Ainv[0, 1] = -A[0, 1] / detA;
                        Ainv[1, 0] = -A[1, 0] / detA;

                        // Calculate intersection
                        float theta = Ainv[0, 0] * pmv[0] + Ainv[0, 1] * pmv[1];
                        float phi = Ainv[1, 0] * pmv[0] + Ainv[1, 1] * pmv[1];

                        // Check if this intersection is actually the edge of the screen
                        if (theta <= 1 && theta >= 0 && phi <= 1 && phi >= 0)
                        {
                            pEnd = pStart + theta * dp;
                            break;
                        }
                    }
                }
            }
            // Make start and finish positions relative to screen size, origin at the screen centre
            //pStart = new Vector2((pStart.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pStart.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
            //pEnd = new Vector2((pEnd.x - transform.position.x + transform.localScale.x / 2) / transform.localScale.x, (pEnd.y - transform.position.y + transform.localScale.y / 2) / transform.localScale.y);
            
            callback(pStart, pEnd, (tEnd - tStart) / 1000.0f);
        }
    }

    private bool IsInside(Vector3 p)
    {
        return p.x <= screenCorners[0].x && p.x >= screenCorners[2].x && p.y >= screenCorners[2].y && p.y <= screenCorners[0].y; 
    }
}
